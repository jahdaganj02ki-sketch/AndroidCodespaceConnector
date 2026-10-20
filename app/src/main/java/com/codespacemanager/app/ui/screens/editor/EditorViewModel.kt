package com.codespacemanager.app.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.local.datastore.SettingsStore
import com.codespacemanager.app.domain.gateway.CodespaceGateway
import com.codespacemanager.app.domain.gateway.PortGateway
import com.codespacemanager.app.domain.gateway.PortVisibility
import com.codespacemanager.app.domain.model.CodespaceState
import com.codespacemanager.app.domain.usecase.DevContainerTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecoveryReason { PORT_CLOSED, CODESPACE_STOPPED }

data class EditorUiState(
    val loading: Boolean = true,
    val portPublic: Boolean = false,
    val recovery: RecoveryReason? = null,
    val showAgentHint: Boolean = false,
    val error: String? = null,
    val codespaceName: String = "",
    val port: Int = DevContainerTemplates.EDITOR_PORT,
)

sealed interface EditorEvent {
    data class Message(val text: String) : EditorEvent
}

/**
 * Editor-ViewModel: verwaltet die Port-Sichtbarkeit, den Idle-Timer und den Recovery-Zustand.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val codespaceGateway: CodespaceGateway,
    private val portGateway: PortGateway,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private var idleJob: Job? = null
    private var tunnelProperties: com.codespacemanager.app.domain.gateway.TunnelProperties? = null

    fun load(codespaceName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, codespaceName = codespaceName)
            try {
                val codespace = codespaceGateway.getCodespace(codespaceName)
                val properties = codespaceGateway.tunnelProperties(codespaceName)
                tunnelProperties = properties
                val isPublic = portGateway.isPublic(properties, _state.value.port)
                _state.value = _state.value.copy(
                    loading = false,
                    portPublic = isPublic,
                    recovery = if (codespace.state == CodespaceState.SHUTDOWN) {
                        RecoveryReason.CODESPACE_STOPPED
                    } else {
                        null
                    },
                    showAgentHint = !settingsStore.current().hasSeenAgentKeyHint,
                )
                if (isPublic) scheduleIdlePrivate()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Codespace konnte nicht geladen werden",
                )
            }
        }
    }

    fun setPortPublic(public: Boolean) {
        val properties = tunnelProperties ?: return
        viewModelScope.launch {
            val result = portGateway.setPort(properties, _state.value.port, visibility =
                if (public) PortVisibility.PUBLIC else PortVisibility.PRIVATE)
            if (result.success) {
                _state.value = _state.value.copy(portPublic = public, recovery = null)
                if (public) scheduleIdlePrivate() else idleJob?.cancel()
            } else {
                _state.value = _state.value.copy(error = result.errorMessage ?: "Port konnte nicht geändert werden")
            }
        }
    }

    /** Jede Interaktion setzt den Idle-Timer zurück. */
    fun onInteraction() {
        if (_state.value.portPublic) scheduleIdlePrivate()
    }

    fun onConnectionError() {
        _state.value = _state.value.copy(recovery = RecoveryReason.PORT_CLOSED)
    }

    fun onPageLoaded() {
        if (_state.value.recovery != null) {
            _state.value = _state.value.copy(recovery = null)
        }
    }

    fun reopenPort() {
        setPortPublic(true)
    }

    fun restartCodespace() {
        val name = _state.value.codespaceName
        viewModelScope.launch {
            runCatching { codespaceGateway.startCodespace(name) }
                .onFailure { _events.tryEmit(EditorEvent.Message(it.message ?: "Neustart fehlgeschlagen")) }
            setPortPublic(true)
        }
    }

    fun dismissAgentHint() {
        viewModelScope.launch {
            settingsStore.update { it.copy(hasSeenAgentKeyHint = true) }
            _state.value = _state.value.copy(showAgentHint = false)
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun scheduleIdlePrivate() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            val minutes = settingsStore.current().idlePrivatePortMinutes.coerceAtLeast(1)
            delay(minutes * 60_000L)
            val properties = tunnelProperties
            if (properties != null && _state.value.portPublic) {
                val result = portGateway.setPort(properties, _state.value.port, PortVisibility.PRIVATE)
                if (result.success) {
                    _state.value = _state.value.copy(portPublic = false)
                    _events.tryEmit(EditorEvent.Message("Port wurde automatisch privat geschaltet"))
                }
            }
        }
    }
}
