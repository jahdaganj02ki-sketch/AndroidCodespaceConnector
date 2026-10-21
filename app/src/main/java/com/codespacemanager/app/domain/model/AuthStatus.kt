package com.codespacemanager.app.domain.model

/**
 * Zustand des laufenden Anmeldeflows. Wird von [com.codespacemanager.app.data.repository.AuthCoordinator]
 * gehalten und vom Login-Screen beobachtet.
 */
sealed interface AuthStatus {
    data object Idle : AuthStatus
    data object Authenticating : AuthStatus
    data class Success(val login: String) : AuthStatus
    data class Error(val message: String) : AuthStatus
}
