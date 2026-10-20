package com.codespacemanager.app.platform.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haelt den laufenden OAuth-Flow (State + Code-Verifier) ueber den Redirect hinweg.
 * Der Prozess der App bleibt beim Redirect im Hintergrund aktiv.
 */
@Singleton
class OAuthSession @Inject constructor() {

    private var pending: OAuthRequest? = null

    fun begin(request: OAuthRequest): OAuthRequest {
        pending = request
        return request
    }

    fun peek(): OAuthRequest? = pending

    fun consume(): OAuthRequest? {
        val current = pending
        pending = null
        return current
    }

    fun clear() {
        pending = null
    }
}
