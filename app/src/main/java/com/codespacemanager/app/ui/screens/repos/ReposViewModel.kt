package com.codespacemanager.app.ui.screens.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.local.datastore.SettingsStore
import com.codespacemanager.app.data.repository.AccountRepository
import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.model.Repo
import com.codespacemanager.app.domain.model.RepoTemplate
import com.codespacemanager.app.domain.usecase.DevContainerTemplates
import com.codespacemanager.app.domain.usecase.DevContainerScriptProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReposUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val repos: List<Repo> = emptyList(),
    val organizations: List<String> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val creating: Boolean = false,
    val nextPage: Int? = null,
) {
    val filtered: List<Repo>
        get() = if (query.isBlank()) {
            repos
        } else {
            repos.filter { it.fullName.contains(query, ignoreCase = true) }
        }
}

sealed interface ReposEvent {
    data class Message(val text: String) : ReposEvent
    data class Created(val repo: Repo) : ReposEvent
}

@HiltViewModel
class ReposViewModel @Inject constructor(
    private val repoGateway: RepoGateway,
    private val accountRepository: AccountRepository,
    private val settingsStore: SettingsStore,
    private val scriptProvider: DevContainerScriptProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ReposUiState())
    val state: StateFlow<ReposUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReposEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ReposEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val page = repoGateway.listUserRepos(1)
                val organizations = runCatching { repoGateway.listOrganizations() }.getOrDefault(emptyList())
                val orgRepos = organizations.flatMap { org ->
                    runCatching { repoGateway.listOrgRepos(org, 1).items }.getOrDefault(emptyList())
                }
                val merged = (page.items + orgRepos).distinctBy { it.id }
                _state.value = _state.value.copy(
                    loading = false,
                    repos = merged.sortedByDescending { it.updatedAt ?: "" },
                    organizations = organizations,
                    nextPage = page.nextPage,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Repos konnten nicht geladen werden",
                )
            }
        }
    }

    fun loadMore() {
        val next = _state.value.nextPage ?: return
        if (_state.value.loadingMore) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            try {
                val page = repoGateway.listUserRepos(next)
                val merged = (_state.value.repos + page.items).distinctBy { it.id }
                _state.value = _state.value.copy(
                    loadingMore = false,
                    repos = merged.sortedByDescending { it.updatedAt ?: "" },
                    nextPage = page.nextPage,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    /**
     * Legt ein Repository an und beschreibt es mit dem devcontainer fuer den Editor.
     */
    fun createRepo(name: String, isPrivate: Boolean, owner: String?, template: RepoTemplate) {
        if (_state.value.creating) return
        viewModelScope.launch {
            _state.value = _state.value.copy(creating = true)
            try {
                val repo = repoGateway.createRepo(
                    name = name,
                    isPrivate = isPrivate,
                    owner = owner,
                    description = "Angelegt mit dem Codespace Manager",
                )
                writeStartFiles(repo, template)
                _state.value = _state.value.copy(
                    creating = false,
                    repos = (_state.value.repos + repo).distinctBy { it.id },
                )
                _events.tryEmit(ReposEvent.Created(repo))
            } catch (e: Exception) {
                _state.value = _state.value.copy(creating = false)
                _events.tryEmit(
                    ReposEvent.Message(e.message ?: "Repository konnte nicht angelegt werden"),
                )
            }
        }
    }

    private suspend fun writeStartFiles(repo: Repo, template: RepoTemplate) {
        val extensions = settingsStore.current().extensionIds
        val files = listOf(
            DevContainerTemplates.DEVCONTAINER_PATH to
                DevContainerTemplates.render(template, extensions),
            DevContainerTemplates.SETUP_SCRIPT_PATH to scriptProvider.setupCodeServerScript(
                extensions = extensions,
                editorPort = DevContainerTemplates.EDITOR_PORT,
            ),
            DevContainerTemplates.EXTENSIONS_TXT_PATH to scriptProvider.extensionsText(extensions),
        )
        files.forEach { (path, content) ->
            runCatching {
                repoGateway.putFileContent(
                    fullName = repo.fullName,
                    path = path,
                    content = content,
                    message = "Codespace Manager: $path",
                    branch = repo.defaultBranch,
                    sha = null,
                )
            }
        }
    }
}
