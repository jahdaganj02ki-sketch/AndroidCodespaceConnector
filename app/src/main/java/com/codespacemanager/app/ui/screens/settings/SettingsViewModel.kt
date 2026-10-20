package com.codespacemanager.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.BuildConfig
import com.codespacemanager.app.data.local.datastore.SettingsStore
import com.codespacemanager.app.data.remote.update.UpdateChecker
import com.codespacemanager.app.domain.gateway.AppSettings
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.usecase.SyncExtensionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val extensionText: String = "",
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val checkingUpdate: Boolean = false,
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class UpdateAvailable(val version: String, val url: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val updateChecker: UpdateChecker,
    private val syncExtensionsUseCase: SyncExtensionsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.value = _state.value.copy(
                    settings = settings,
                    extensionText = settings.extensionIds.joinToString("\n"),
                )
            }
        }
    }

    fun setClientId(value: String) = update { it.copy(clientIdOverride = value) }

    fun setClientSecret(value: String) = update { it.copy(clientSecretOverride = value) }

    fun setGeo(geo: Geo) = update { it.copy(defaultGeo = geo) }

    fun setMachine(machine: String) = update { it.copy(defaultMachine = machine) }

    fun setIdleTimeout(minutes: Int) = update { it.copy(idleTimeoutMinutes = minutes) }

    fun setIdlePrivateMinutes(minutes: Int) = update { it.copy(idlePrivatePortMinutes = minutes) }

    fun setAutoPrivate(enabled: Boolean) = update { it.copy(autoPrivatePortOnExit = enabled) }

    fun setExtensions(text: String) {
        _state.value = _state.value.copy(extensionText = text)
    }

    fun saveExtensions() {
        viewModelScope.launch {
            val ids = _state.value.extensionText
                .split("\n", ",", " ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            settingsStore.update { it.copy(extensionIds = ids) }
            _events.tryEmit(SettingsEvent.Message("Erweiterungsliste gespeichert (${ids.size} Einträge)"))
        }
    }

    /** Loest die Erweiterungen auf Open VSX auf und erzeugt die Lock-Datei. */
    fun resolveExtensions() {
        viewModelScope.launch {
            val ids = settingsStore.current().extensionIds
            val lockFile = syncExtensionsUseCase.buildLockFile(ids)
            val resolved = syncExtensionsUseCase.resolveAll(ids)
            val missing = ids.filter { id -> resolved.none { it.id == id } }
            _events.tryEmit(
                SettingsEvent.Message(
                    if (missing.isEmpty()) {
                        "Alle Erweiterungen aufgelöst – Lock-Datei:\n$lockFile"
                    } else {
                        "Nicht auflösbar: ${missing.joinToString(", ")}\nLock-Datei:\n$lockFile"
                    },
                ),
            )
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(checkingUpdate = true)
            val info = updateChecker.check()
            _state.value = _state.value.copy(checkingUpdate = false)
            if (info == null) {
                _events.tryEmit(SettingsEvent.Message("Update-Status konnte nicht geladen werden"))
            } else if (UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                _events.tryEmit(SettingsEvent.UpdateAvailable(info.version, info.url))
            } else {
                _events.tryEmit(SettingsEvent.Message("Du hast bereits die neueste Version"))
            }
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }
}
