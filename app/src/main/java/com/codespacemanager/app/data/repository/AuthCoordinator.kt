package com.codespacemanager.app.data.repository

import com.codespacemanager.app.data.local.CredentialsProvider
import com.codespacemanager.app.domain.model.Account
import com.codespacemanager.app.platform.auth.OAuth
import com.codespacemanager.app.platform.auth.OAuthCallback
import com.codespacemanager.app.platform.auth.OAuthRequest
import com.codespacemanager.app.platform.auth.OAuthSession
import javax.inject.Inject
import javax.inject.Singleton

/** Koordiniert den kompletten OAuth-Flow (Autorisierungs-URL, Redirect, Token-Tausch). */
@Singleton
class AuthCoordinator @Inject constructor(
    private val credentialsProvider: CredentialsProvider,
    private val accountRepository: AccountRepository,
    private val oauthSession: OAuthSession,
) {

    suspend fun authorizeUrl(login: String? = null): String? {
        val clientId = credentialsProvider.clientId()
        if (clientId.isBlank()) return null
        val request = oauthSession.begin(OAuthRequest.create())
        return OAuth.authorizeUrl(clientId, request, login)
    }

    /** Verarbeitet den Redirect. Gibt das angemeldete Konto oder einen Fehler zurueck. */
    suspend fun handleCallback(uri: String): Result<Account> {
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
