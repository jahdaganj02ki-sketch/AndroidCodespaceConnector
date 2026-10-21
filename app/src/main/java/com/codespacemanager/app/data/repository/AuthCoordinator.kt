package com.codespacemanager.app.data.repository

import com.codespacemanager.app.data.local.CredentialsProvider
import com.codespacemanager.app.domain.model.Account
import com.codespacemanager.app.domain.model.AuthStatus
import com.codespacemanager.app.platform.auth.OAuth
import com.codespacemanager.app.platform.auth.OAuthCallback
import com.codespacemanager.app.platform.auth.OAuthRequest
import com.codespacemanager.app.platform.auth.OAuthSession
import com.codespacemanager.app.platform.auth.SignInProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Koordiniert den kompletten OAuth-Flow (Autorisierungs-URL, Redirect, Token-Tausch). */
@Singleton
class AuthCoordinator @Inject constructor(
    private val credentialsProvider: CredentialsProvider,
    private val accountRepository: AccountRepository,
    private val oauthSession: OAuthSession,
) {

    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Idle)
    val status: StateFlow<AuthStatus> = _status.asStateFlow()

    /**
     * Baut die Autorisierungs-URL. `null`, wenn keine Client-ID hinterlegt ist.
     * Der Provider steuert, ob GitHub die Kontoauswahl (`GITHUB`) oder die volle
     * Anmeldeseite inkl. „Continue with Google" (`GOOGLE`) zeigt.
     */
    suspend fun authorizeUrl(
        provider: SignInProvider = SignInProvider.GITHUB,
        login: String? = null,
    ): String? {
        val clientId = credentialsProvider.clientId()
        if (clientId.isBlank()) return null
        val request = oauthSession.begin(OAuthRequest.create())
        return OAuth.authorizeUrl(clientId, request, provider, login)
    }

    /** Verarbeitet den Redirect. Gibt das angemeldete Konto oder einen Fehler zurueck. */
    suspend fun handleCallback(uri: String): Result<Account> {
        if (_status.value == AuthStatus.Authenticating) {
            return Result.failure(IllegalStateException("Anmeldung läuft bereits"))
        }
        _status.value = AuthStatus.Authenticating
        val result = exchangeCallback(uri)
        _status.value = result.fold(
            onSuccess = { AuthStatus.Success(it.login) },
            onFailure = { AuthStatus.Error(it.message ?: "Anmeldung fehlgeschlagen") },
        )
        return result
    }

    /** Setzt den Status zurueck (z.B. wenn der Fehler geschlossen wird). */
    fun resetStatus() {
        _status.value = AuthStatus.Idle
    }

    private suspend fun exchangeCallback(uri: String): Result<Account> {
        val request = oauthSession.consume()
            ?: return Result.failure(IllegalStateException("Kein laufender Login gefunden"))
        return when (val parsed = OAuth.parseCallback(uri)) {
            is OAuthCallback.Failure -> Result.failure(
                IllegalStateException(parsed.description ?: "Anmeldung fehlgeschlagen (${parsed.error})"),
            )

            is OAuthCallback.Success -> {
                if (parsed.state != null && parsed.state != request.state) {
                    Result.failure(IllegalStateException("Ungültiger State – Anmeldung abgebrochen"))
                } else {
                    runCatching {
                        accountRepository.addAccount(
                            clientId = credentialsProvider.clientId(),
                            clientSecret = credentialsProvider.clientSecret(),
                            code = parsed.code,
                            codeVerifier = request.codeVerifier,
                            redirectUri = request.redirectUri,
                        )
                    }
                }
            }
        }
    }
}
