package com.codespacemanager.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.local.CredentialsProvider
import com.codespacemanager.app.data.local.datastore.SettingsStore
import com.codespacemanager.app.data.repository.AuthCoordinator
import com.codespacemanager.app.domain.model.AuthStatus
import com.codespacemanager.app.platform.auth.SignInProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    /** Client-ID vorhanden (BuildConfig oder Einstellung). */
    val hasClientId: Boolean = false,
    /** Client-Secret vorhanden (BuildConfig oder Einstellung). */
    val hasClientSecret: Boolean = false,
    /** Client-ID und Client-Secret vorhanden – der Token-Tausch ist damit moeglich. */
    val hasCredentials: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    /** Klappabschnitt „OAuth-App konfigurieren". */
    val configExpanded: Boolean = false,
    val status: AuthStatus = AuthStatus.Idle,
    /** Kurzer Hinweis (z.B. fehlende Client-ID). */
    val notice: String? = null,
)

sealed interface LoginEvent {
    /** Die Autorisierungs-URL muss im Custom Tab geoeffnet werden. */
    data class OpenUrl(val url: String) : LoginEvent

    /** Es fehlt eine Client-ID – der Konfigurationsabschnitt wird benoetigt. */
    data object NeedsConfiguration : LoginEvent
}

/** ViewModel des Login-Screens: startet den OAuth-Flow und verwaltet die OAuth-Konfiguration. */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authCoordinator: AuthCoordinator,
    private val credentialsProvider: CredentialsProvider,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    private var lastProvider: SignInProvider = SignInProvider.GITHUB

    init {
        viewModelScope.launch {
            val settings = settingsStore.current()
            val hasClientId = credentialsProvider.clientId().isNotBlank()
            val hasClientSecret = credentialsProvider.clientSecret().isNotBlank()
            _state.value = _state.value.copy(
                clientId = settings.clientIdOverride,
                clientSecret = settings.clientSecretOverride,
                hasClientId = hasClientId,
                hasClientSecret = hasClientSecret,
                hasCredentials = credentialsProvider.hasCredentials(),
                configExpanded = !hasClientId,
            )
        }
        viewModelScope.launch {
            authCoordinator.status.collect { status ->
                _state.value = _state.value.copy(status = status)
            }
        }
    }

    /** Startet den Anmeldeflow und gibt die URL zum Oeffnen im Browser frei. */
    fun signIn(provider: SignInProvider) {
        lastProvider = provider
        viewModelScope.launch {
            val url = authCoordinator.authorizeUrl(provider)
            if (url == null) {
                _state.value = _state.value.copy(
                    configExpanded = true,
                    notice = "Keine Client-ID hinterlegt – bitte unten konfigurieren.",
                )
                _events.tryEmit(LoginEvent.NeedsConfiguration)
            } else {
                _state.value = _state.value.copy(notice = null)
                _events.tryEmit(LoginEvent.OpenUrl(url))
            }
        }
    }

    /** Wiederholt den zuletzt gewaehlten Anmeldeweg. */
    fun retry() {
        signIn(lastProvider)
    }

    fun setClientId(value: String) {
        _state.value = _state.value.copy(clientId = value)
    }

    fun setClientSecret(value: String) {
        _state.value = _state.value.copy(clientSecret = value)
    }

    fun toggleConfig() {
        _state.value = _state.value.copy(configExpanded = !_state.value.configExpanded)
    }

    /** Speichert Client-ID und Client-Secret als Override in den Einstellungen. */
    fun saveOauthConfig() {
        viewModelScope.launch {
            val current = _state.value
            settingsStore.update {
                it.copy(
                    clientIdOverride = current.clientId.trim(),
                    clientSecretOverride = current.clientSecret.trim(),
                )
            }
            val hasClientId = credentialsProvider.clientId().isNotBlank()
            val hasClientSecret = credentialsProvider.clientSecret().isNotBlank()
            _state.value = _state.value.copy(
                hasClientId = hasClientId,
                hasClientSecret = hasClientSecret,
                hasCredentials = credentialsProvider.hasCredentials(),
                configExpanded = !hasClientId,
                notice = if (hasClientId) "Zugangsdaten gespeichert" else "Client-ID fehlt weiterhin",
            )
            if (hasClientId) {
                delay(NOTICE_DURATION_MILLIS)
                _state.value = _state.value.copy(notice = null)
            }
        }
    }

    /** Schliesst die Fehlermeldung. */
    fun dismissError() {
        authCoordinator.resetStatus()
    }

    /** Setzt den Status auf „bereit" zurueck (z.B. nach einem erfolgreichen Login). */
    fun clearStatus() {
        authCoordinator.resetStatus()
    }

    private companion object {
        const val NOTICE_DURATION_MILLIS = 3_000L
    }
}
