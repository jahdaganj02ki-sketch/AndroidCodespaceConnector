package com.codespacemanager.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.local.datastore.SettingsStore
import com.codespacemanager.app.data.repository.AuthCoordinator
import com.codespacemanager.app.domain.gateway.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand des OAuth-Logins. */
sealed interface LoginState {
    data object Idle : LoginState
    data object Authenticating : LoginState
    data class Success(val login: String) : LoginState
    data class Error(val message: String) : LoginState
}

/**
 * ViewModel der Activity: verarbeitet den Redirect (`com.codespacemanager.app://oauth/callback`).
 * Die Activity ist `singleTask`, der ViewModel ueberlebt den Redirect.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authCoordinator: AuthCoordinator,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    val settings: Flow<AppSettings> = settingsStore.settings

    val hasCredentials: StateFlow<Boolean> = settingsStore.settings
        .map { it.hasCredentials }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Verarbeitet den Redirect. */
    fun handleCallback(uri: String) {
        if (_loginState.value == LoginState.Authenticating) return
        _loginState.value = LoginState.Authenticating
        viewModelScope.launch {
            authCoordinator.handleCallback(uri)
                .onSuccess { _loginState.value = LoginState.Success(it.login) }
                .onFailure {
                    _loginState.value = LoginState.Error(it.message ?: "Anmeldung fehlgeschlagen")
                }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
}
