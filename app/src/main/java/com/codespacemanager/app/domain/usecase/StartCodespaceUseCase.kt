package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.domain.gateway.CodespaceGateway
import com.codespacemanager.app.domain.gateway.PortGateway
import com.codespacemanager.app.domain.gateway.PortVisibility
import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.CodespaceState
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.model.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Zustaende der State-Machine "Codespace starten". */
sealed interface StartCodespaceState {

    data object Idle : StartCodespaceState

    data class CheckingRepo(val repoFullName: String) : StartCodespaceState

    data class PreparingRepo(val repoFullName: String) : StartCodespaceState

    data class CreatingCodespace(val repoFullName: String) : StartCodespaceState

    data class WaitingForCodespace(
        val codespaceName: String,
        val attempt: Int,
        val maxAttempts: Int,
    ) : StartCodespaceState

    data class ConnectingPort(val codespaceName: String) : StartCodespaceState

    data class PreparingExtensions(
        val codespaceName: String,
        val portPublic: Boolean,
    ) : StartCodespaceState

    data class WaitingForEditor(
        val codespaceName: String,
        val browseUrl: String,
    ) : StartCodespaceState

    data class Ready(
        val codespaceName: String,
        val browseUrl: String,
        val portPublic: Boolean,
        val browserFallbackUrl: String,
    ) : StartCodespaceState

    data class Failed(
        val message: String,
        val codespaceName: String? = null,
        val allowPermissionsUrl: String? = null,
        val browserFallbackUrl: String? = null,
        val quotaHint: Boolean = false,
        val offerNewCodespace: Boolean = false,
    ) : StartCodespaceState

    data object Cancelled : StartCodespaceState
}

/** Parameter fuer den Start eines Codespaces. */
data class StartCodespaceParams(
    val repo: Repo,
    val machine: String,
    val geo: Geo,
    val idleTimeoutMinutes: Int,
    val ref: String = DevContainerTemplates.EDITOR_BRANCH,
    val devcontainerPath: String? = null,
    val displayName: String? = null,
    val preparedPlan: RepoPreparationPlan? = null,
)

/** Ergebnis einer Editor-Erreichbarkeitspruefung. */
enum class EditorProbeResult { OK, STARTING, UNREACHABLE }

interface EditorHealthGateway {
    suspend fun probe(url: String): EditorProbeResult
}

/**
 * State-Machine: PruefeRepo -> BereiteRepo(optional) -> ErzeugeCodespace ->
 * WarteAufCodespace -> VerbindePort -> WarteAufEditor -> Editor.
 */
class StartCodespaceUseCase @Inject constructor(
    private val codespaceGateway: CodespaceGateway,
    private val repoGateway: RepoGateway,
    private val portGateway: PortGateway,
    private val prepareRepoUseCase: PrepareRepoUseCase,
    private val editorHealthGateway: EditorHealthGateway,
) {

    suspend fun start(params: StartCodespaceParams): Flow<StartCodespaceState> = flow {
        val editorPort = DevContainerTemplates.EDITOR_PORT

        // 1) Repo pruefen
        emit(StartCodespaceState.CheckingRepo(params.repo.fullName))
        val permissions = try {
            repoGateway.checkPermissions(params.repo.id, params.ref, params.devcontainerPath)
        } catch (e: Exception) {
            emit(StartCodespaceState.Failed(e.message ?: "Repo konnte nicht geprueft werden"))
            return@flow
        }
        if (!permissions.canCreateCodespaces) {
            emit(
                StartCodespaceState.Failed(
                    message = "Für dieses Konto/Repo sind keine Codespaces verfügbar. " +
                        "Prüfe dein Kontingent unter github.com/settings/codespaces.",
                    quotaHint = true,
                ),
            )
            return@flow
        }

        // 2) Branch/Devcontainer vorbereiten (optional)
        var devcontainerPath = params.devcontainerPath
        params.preparedPlan?.let { plan ->
            emit(StartCodespaceState.PreparingRepo(params.repo.fullName))
            when (val result = prepareRepoUseCase.apply(plan)) {
                is PrepareRepoResult.Failed -> {
                    emit(StartCodespaceState.Failed(result.message))
                    return@flow
                }

                is PrepareRepoResult.Applied -> devcontainerPath = result.plan.devcontainerPath
                is PrepareRepoResult.NeedsConfirmation -> Unit
            }
        }

        // 3) Codespace erzeugen oder wiederverwenden
        emit(StartCodespaceState.CreatingCodespace(params.repo.fullName))
        val codespace = try {
            resolveCodespace(params, devcontainerPath)
        } catch (e: ApiException) {
            emit(e.toFailedState())
            return@flow
        } catch (e: Exception) {
            emit(StartCodespaceState.Failed(e.message ?: "Codespace konnte nicht erstellt werden"))
            return@flow
        }

        // 4) Auf laufenden Codespace warten
        val running = waitForCodespace(codespace.name) { emit(it) }
        if (running == null) {
            emit(
                StartCodespaceState.Failed(
                    message = "Der Codespace wurde nicht rechtzeitig verfügbar. " +
                        "Du kannst ihn im Browser öffnen und dort weiterarbeiten.",
                    codespaceName = codespace.name,
                    browserFallbackUrl = CodespaceUrls.codespaceWebUi(codespace.name),
                ),
            )
            return@flow
        }

        // 5) Port oeffnen
        emit(StartCodespaceState.ConnectingPort(running.name))
        val properties = try {
            codespaceGateway.tunnelProperties(running.name)
        } catch (e: Exception) {
            emit(
                StartCodespaceState.Failed(
                    message = "Tunnel-Credentials konnten nicht geladen werden. " +
                        "Der Codespace läuft, arbeite im Browser weiter.",
                    codespaceName = running.name,
                    browserFallbackUrl = CodespaceUrls.codespaceWebUi(running.name),
                ),
            )
            return@flow
        }

        val privacy = running.allowedPortPrivacySettings
        val publicAllowed = privacy.isEmpty() || privacy.any { it.equals("public", ignoreCase = true) }
        val targetVisibility = if (publicAllowed) PortVisibility.PUBLIC else PortVisibility.PRIVATE
        val portResult = portGateway.setPort(properties, editorPort, targetVisibility)
        if (!portResult.success && publicAllowed) {
            emit(
                StartCodespaceState.Failed(
                    message = "Der Editor-Port konnte nicht geöffnet werden. " +
                        "Der Codespace läuft, arbeite im Browser weiter.",
                    codespaceName = running.name,
                    browserFallbackUrl = CodespaceUrls.codespaceWebUi(running.name),
                ),
            )
            return@flow
        }

        // 6) Erweiterungen/Editor werden vorbereitet
        emit(StartCodespaceState.PreparingExtensions(running.name, publicAllowed))
        val browseUrl = CodespaceUrls.browseUrl(running.name, editorPort)
        emit(StartCodespaceState.WaitingForEditor(running.name, browseUrl))

        if (!waitForEditor(browseUrl)) {
            emit(
                StartCodespaceState.Failed(
                    message = "Der Editor hat nicht auf die Anfrage reagiert. " +
                        "Prüfe das devcontainer oder öffne den Codespace im Browser.",
                    codespaceName = running.name,
                    browserFallbackUrl = CodespaceUrls.codespaceWebUi(running.name),
                    offerNewCodespace = true,
                ),
            )
            return@flow
        }

        emit(
            StartCodespaceState.Ready(
                codespaceName = running.name,
                browseUrl = browseUrl,
                portPublic = publicAllowed,
                browserFallbackUrl = CodespaceUrls.codespaceWebUi(running.name),
            ),
        )
    }

    private suspend fun resolveCodespace(
        params: StartCodespaceParams,
        devcontainerPath: String?,
    ): Codespace {
        val existing = codespaceGateway.listRepoCodespaces(params.repo.fullName)
            .filter { codespace ->
                // Nur wiederverwenden, wenn derselbe devcontainer genutzt wird.
                // Codespaces aus der Web-UI mit anderem devcontainer laufen ohne code-server.
                val samePath = when {
                    devcontainerPath == null -> true
                    codespace.devcontainerPath == null -> true
                    else -> codespace.devcontainerPath.equals(devcontainerPath, ignoreCase = true)
                }
                samePath && codespace.state != CodespaceState.SHUTTING_DOWN
            }
            .firstOrNull()

        if (existing != null) {
            if (existing.state == CodespaceState.SHUTDOWN) {
                codespaceGateway.startCodespace(existing.name)
            }
            return existing
        }

        return codespaceGateway.createCodespace(
            repositoryId = params.repo.id,
            ref = params.ref,
            machine = params.machine,
            geo = params.geo,
            location = null,
            idleTimeoutMinutes = params.idleTimeoutMinutes,
            devcontainerPath = devcontainerPath,
            displayName = params.displayName ?: "${params.repo.name} (App)",
        )
    }

    private suspend fun waitForCodespace(
        name: String,
        emitState: suspend (StartCodespaceState) -> Unit,
    ): Codespace? {
        repeat(CODESPACE_POLL_ATTEMPTS) { attempt ->
            val codespace = try {
                codespaceGateway.getCodespace(name)
            } catch (e: Exception) {
                null
            }
            if (codespace != null && codespace.state == CodespaceState.AVAILABLE) return codespace
            if (codespace != null && codespace.state == CodespaceState.SHUTDOWN) return null
            emitState(
                StartCodespaceState.WaitingForCodespace(name, attempt + 1, CODESPACE_POLL_ATTEMPTS),
            )
            delay(CODESPACE_POLL_INTERVAL_MS)
        }
        return null
    }

    private suspend fun waitForEditor(url: String): Boolean {
        repeat(EDITOR_POLL_ATTEMPTS) {
            when (editorHealthGateway.probe(url)) {
                EditorProbeResult.OK -> return true
                EditorProbeResult.STARTING -> delay(EDITOR_POLL_INTERVAL_MS)
                EditorProbeResult.UNREACHABLE -> delay(EDITOR_POLL_INTERVAL_MS)
            }
        }
        return false
    }

    private fun ApiException.toFailedState(): StartCodespaceState.Failed = when {
        isUnauthorized && allowPermissionsUrl != null -> StartCodespaceState.Failed(
            message = "Der devcontainer verlangt zusätzliche Repository-Berechtigungen.",
            allowPermissionsUrl = allowPermissionsUrl,
        )

        isForbidden -> StartCodespaceState.Failed(
            message = "Für dieses Konto/Repo sind keine Codespaces verfügbar. " +
                "Prüfe dein Kontingent unter github.com/settings/codespaces.",
            quotaHint = true,
        )

        else -> StartCodespaceState.Failed(message = message)
    }

    companion object {
        const val CODESPACE_POLL_ATTEMPTS = 120
        const val CODESPACE_POLL_INTERVAL_MS = 5_000L
        const val EDITOR_POLL_ATTEMPTS = 36
        const val EDITOR_POLL_INTERVAL_MS = 5_000L
    }
}
