package com.codespacemanager.app.ui.screens.codespaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.repository.AccountRepository
import com.codespacemanager.app.domain.gateway.CodespaceGateway
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.usecase.QuotaEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodespacesUiState(
    val loading: Boolean = true,
    val codespaces: List<Codespace> = emptyList(),
    val activeAccount: String? = null,
    val error: String? = null,
    val coreHoursEstimate: Double = 0.0,
)

sealed interface CodespacesEvent {
    data class Message(val text: String) : CodespacesEvent
    data class OpenEditor(val codespaceName: String) : CodespacesEvent
}

@HiltViewModel
class CodespacesViewModel @Inject constructor(
    private val codespaceGateway: CodespaceGateway,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CodespacesUiState())
    val state: StateFlow<CodespacesUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CodespacesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CodespacesEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val codespaces = codespaceGateway.listCodespaces()
                _state.value = _state.value.copy(
                    loading = false,
                    codespaces = codespaces.sortedBy { it.name },
                    activeAccount = accountRepository.activeAccountLogin(),
                    coreHoursEstimate = QuotaEstimator.estimateCoreHours(codespaces),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Codespaces konnten nicht geladen werden",
                )
            }
        }
    }

    fun stop(codespace: Codespace) {
        viewModelScope.launch {
            runCatching { codespaceGateway.stopCodespace(codespace.name) }
                .onSuccess { _events.tryEmit(CodespacesEvent.Message("${codespace.name} wird gestoppt")) }
                .onFailure { _events.tryEmit(CodespacesEvent.Message(it.message ?: "Stoppen fehlgeschlagen")) }
            refresh()
        }
    }

    fun delete(codespace: Codespace) {
        viewModelScope.launch {
            runCatching { codespaceGateway.deleteCodespace(codespace.name) }
                .onSuccess { _events.tryEmit(CodespacesEvent.Message("${codespace.name} gelöscht")) }
                .onFailure { _events.tryEmit(CodespacesEvent.Message(it.message ?: "Löschen fehlgeschlagen")) }
            refresh()
        }
    }

    fun rename(codespace: Codespace, displayName: String) {
        viewModelScope.launch {
            runCatching { codespaceGateway.renameCodespace(codespace.name, displayName) }
                .onSuccess { _events.tryEmit(CodespacesEvent.Message("Umbenannt")) }
                .onFailure { _events.tryEmit(CodespacesEvent.Message(it.message ?: "Umbenennen fehlgeschlagen")) }
            refresh()
        }
    }
}
