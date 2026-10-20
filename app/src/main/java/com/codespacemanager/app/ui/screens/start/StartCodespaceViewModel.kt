package com.codespacemanager.app.ui.screens.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.local.datastore.SettingsStore
import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.model.Machine
import com.codespacemanager.app.domain.usecase.PrepareRepoResult
import com.codespacemanager.app.domain.usecase.PrepareRepoUseCase
import com.codespacemanager.app.domain.usecase.RepoPreparationPlan
import com.codespacemanager.app.domain.usecase.StartCodespaceParams
import com.codespacemanager.app.domain.usecase.StartCodespaceState
import com.codespacemanager.app.domain.usecase.StartCodespaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StartUiState(
    val loading: Boolean = false,
    val repoName: String = "",
    val machine: String = "",
    val geo: Geo = Geo.EUROPE_WEST,
    val idleTimeoutMinutes: Int = 30,
    val machines: List<Machine> = emptyList(),
    val plan: RepoPreparationPlan? = null,
    val progress: StartCodespaceState? = null,
    val error: String? = null,
    val canCancel: Boolean = false,
)

sealed interface StartEvent {
    data class Message(val text: String) : StartEvent
    data class OpenUrl(val url: String) : StartEvent
    data class OpenEditor(val codespaceName: String) : StartEvent
}

@HiltViewModel
class StartCodespaceViewModel @Inject constructor(
    private val repoGateway: RepoGateway,
    private val prepareRepoUseCase: PrepareRepoUseCase,
    private val startCodespaceUseCase: StartCodespaceUseCase,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(StartUiState())
    val state: StateFlow<StartUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StartEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StartEvent> = _events.asSharedFlow()

    private var startJob: Job? = null
    private var repoFullName: String = ""

    fun start(fullName: String) {
        repoFullName = fullName
        viewModelScope.launch {
            val settings = settingsStore.current()
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                repoName = fullName,
                machine = settings.defaultMachine,
                geo = settings.defaultGeo,
                idleTimeoutMinutes = settings.idleTimeoutMinutes,
            )
            try {
                val repo = repoGateway.getRepo(fullName)
                val machines = runCatching {
                    repoGateway.listMachines(repo.id, null, null, null)
                }.getOrDefault(emptyList())
                _state.value = _state.value.copy(machines = machines, repoName = repo.fullName)
                when (val preparation = prepareRepoUseCase.prepare(repo)) {
                    is PrepareRepoResult.NeedsConfirmation -> {
                        _state.value = _state.value.copy(loading = false, plan = preparation.plan)
                    }

                    is PrepareRepoResult.Applied -> {
                        _state.value = _state.value.copy(loading = false)
                        runStateMachine(preparation.plan)
                    }

                    is PrepareRepoResult.Failed -> {
                        _state.value = _state.value.copy(loading = false, error = preparation.message)
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Codespace konnte nicht vorbereitet werden",
                )
            }
        }
    }

    /** Der Diff wurde bestaetigt: Dateien schreiben und starten. */
    fun confirmPreparation() {
        val plan = _state.value.plan ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, plan = null)
            when (val result = prepareRepoUseCase.apply(plan)) {
                is PrepareRepoResult.Applied -> {
                    _state.value = _state.value.copy(loading = false)
                    runStateMachine(result.plan)
                }

                is PrepareRepoResult.Failed -> {
                    _state.value = _state.value.copy(loading = false, error = result.message)
                }

                is PrepareRepoResult.NeedsConfirmation -> {
                    _state.value = _state.value.copy(loading = false, plan = result.plan)
                }
            }
        }
    }

    /** Ohne Aenderung starten (Original devcontainer bleibt erhalten). */
    fun skipPreparation() {
        val plan = _state.value.plan
        _state.value = _state.value.copy(plan = null, loading = false)
        runStateMachine(plan)
    }

    fun setMachine(machine: String) {
        _state.value = _state.value.copy(machine = machine)
    }

    fun setGeo(geo: Geo) {
        _state.value = _state.value.copy(geo = geo)
    }

    fun setIdleTimeout(minutes: Int) {
        _state.value = _state.value.copy(idleTimeoutMinutes = minutes)
    }

    fun cancel() {
        startJob?.cancel()
        startJob = null
        _state.value = _state.value.copy(canCancel = false, progress = StartCodespaceState.Cancelled)
    }

    private fun runStateMachine(plan: RepoPreparationPlan?) {
        startJob?.cancel()
        startJob = viewModelScope.launch {
            val repo = runCatching { repoGateway.getRepo(repoFullName) }.getOrNull()
            if (repo == null) {
                _state.value = _state.value.copy(error = "Repository konnte nicht geladen werden")
                return@launch
            }
            val params = StartCodespaceParams(
                repo = repo,
                machine = _state.value.machine,
                geo = _state.value.geo,
                idleTimeoutMinutes = _state.value.idleTimeoutMinutes,
                devcontainerPath = plan?.devcontainerPath,
                preparedPlan = plan,
            )
            _state.value = _state.value.copy(canCancel = true, error = null)
            startCodespaceUseCase.start(params).collect { progress ->
                _state.value = _state.value.copy(progress = progress)
                when (progress) {
                    is StartCodespaceState.Ready -> {
                        _state.value = _state.value.copy(canCancel = false)
                        _events.tryEmit(StartEvent.OpenEditor(progress.codespaceName))
                    }

                    is StartCodespaceState.Failed -> {
                        _state.value = _state.value.copy(canCancel = false, error = progress.message)
                        progress.allowPermissionsUrl?.let { _events.tryEmit(StartEvent.OpenUrl(it)) }
                        progress.browserFallbackUrl?.let { _events.tryEmit(StartEvent.OpenUrl(it)) }
                    }

                    else -> Unit
                }
            }
            _state.value = _state.value.copy(canCancel = false)
        }
    }
}
