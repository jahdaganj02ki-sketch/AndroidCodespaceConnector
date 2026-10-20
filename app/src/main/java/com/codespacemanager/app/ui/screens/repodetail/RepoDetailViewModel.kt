package com.codespacemanager.app.ui.screens.repodetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.domain.gateway.CodespaceGateway
import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.Repo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoDetailUiState(
    val loading: Boolean = true,
    val repo: Repo? = null,
    val codespaces: List<Codespace> = emptyList(),
    val error: String? = null,
)

sealed interface RepoDetailEvent {
    data class Message(val text: String) : RepoDetailEvent
}

@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    private val repoGateway: RepoGateway,
    private val codespaceGateway: CodespaceGateway,
) : ViewModel() {

    private val _state = MutableStateFlow(RepoDetailUiState())
    val state: StateFlow<RepoDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RepoDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RepoDetailEvent> = _events.asSharedFlow()

    private var repoFullName: String = ""

    fun load(fullName: String) {
        repoFullName = fullName
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val repo = repoGateway.getRepo(fullName)
                val codespaces = runCatching { codespaceGateway.listRepoCodespaces(fullName) }
                    .getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    loading = false,
                    repo = repo,
                    codespaces = codespaces,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Repository konnte nicht geladen werden",
                )
            }
        }
    }

    fun stop(codespace: Codespace) {
        viewModelScope.launch {
            runCatching { codespaceGateway.stopCodespace(codespace.name) }
                .onFailure { _events.tryEmit(RepoDetailEvent.Message(it.message ?: "Stoppen fehlgeschlagen")) }
            load(repoFullName)
        }
    }

    fun delete(codespace: Codespace) {
        viewModelScope.launch {
            runCatching { codespaceGateway.deleteCodespace(codespace.name) }
                .onFailure { _events.tryEmit(RepoDetailEvent.Message(it.message ?: "Löschen fehlgeschlagen")) }
            load(repoFullName)
        }
    }
}
