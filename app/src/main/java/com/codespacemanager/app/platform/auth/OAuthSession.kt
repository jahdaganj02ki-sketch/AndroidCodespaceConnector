package com.codespacemanager.app.platform.auth

import com.codespacemanager.app.domain.gateway.SettingsGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haelt den laufenden OAuth-Flow (State + Code-Verifier) ueber den Redirect hinweg.
 * Der Flow liegt im DataStore, damit ein Prozessneustart waehrend des
 * Browser-Rundgangs den Login nicht zerschiesst.
 */
@Singleton
class OAuthSession @Inject constructor(
    private val settingsGateway: SettingsGateway,
) {

    suspend fun begin(request: OAuthRequest): OAuthRequest {
        settingsGateway.update {
            it.copy(
                pendingOAuthState = request.state,
                pendingOAuthVerifier = request.codeVerifier,
            )
        }
        return request
    }

    /** Holt den laufenden Flow und loescht ihn – ein zweiter Aufruf liefert `null`. */
    suspend fun consume(): OAuthRequest? {
        val settings = settingsGateway.current()
        val state = settings.pendingOAuthState
        val verifier = settings.pendingOAuthVerifier
        if (state.isBlank() || verifier.isBlank()) return null
        settingsGateway.update {
            it.copy(pendingOAuthState = "", pendingOAuthVerifier = "")
        }
        return OAuthRequest(state = state, codeVerifier = verifier)
    }
}
