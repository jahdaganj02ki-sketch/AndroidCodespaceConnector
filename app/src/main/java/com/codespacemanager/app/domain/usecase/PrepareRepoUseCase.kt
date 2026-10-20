package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.gateway.SettingsGateway
import com.codespacemanager.app.domain.model.Repo
import com.codespacemanager.app.domain.model.RepoTemplate
import javax.inject.Inject

/** Datei, die in den Editor-Branch geschrieben werden soll. */
data class PreparedFile(
    val path: String,
    val content: String,
    val commitMessage: String,
)

/** Vollstaendiger Plan fuer die Repo-Vorbereitung. */
data class RepoPreparationPlan(
    val repoFullName: String,
    val branch: String,
    val devcontainerPath: String,
    val originalDevcontainer: String?,
    val devcontainerContent: String,
    val diff: String,
    val files: List<PreparedFile>,
    val notes: List<String>,
) {
    val hasChanges: Boolean get() = files.isNotEmpty()
}

/** Ergebnis der Repo-Vorbereitung. */
sealed interface PrepareRepoResult {
    /** Alles vorbereitet und geschrieben. */
    data class Applied(val plan: RepoPreparationPlan) : PrepareRepoResult

    /** Aenderungen muessen bestaetigt werden (Diff anzeigen). */
    data class NeedsConfirmation(val plan: RepoPreparationPlan) : PrepareRepoResult

    data class Failed(val message: String) : PrepareRepoResult
}

/** Liefert die Skripte, die in ein Repository geschrieben werden. */
interface DevContainerScriptProvider {
    suspend fun setupCodeServerScript(extensions: List<String>, editorPort: Int): String
    suspend fun setupAndroidScript(): String
    suspend fun extensionsText(extensions: List<String>): String
}

/**
 * Bereitet ein Repository fuer den Editor-Codespace vor:
 *  * Repo ohne devcontainer  -> Standard-Template auf Branch `codespace/editor`
 *  * Repo mit devcontainer   -> JSON-Merge, Original bleibt im Default-Branch
 */
class PrepareRepoUseCase @Inject constructor(
    private val repoGateway: RepoGateway,
    private val scriptProvider: DevContainerScriptProvider,
    private val settingsGateway: SettingsGateway,
) {

    suspend fun prepare(
        repo: Repo,
        template: RepoTemplate = RepoTemplate.STANDARD,
    ): PrepareRepoResult {
        return try {
            val extensions = settingsGateway.current().extensionIds
            val devcontainers = repoGateway.listDevContainers(repo.id, repo.defaultBranch)
            val existing = devcontainers.firstOrNull()
            val existingContent = existing?.let {
                repoGateway.getFileContent(repo.fullName, it.path, repo.defaultBranch)?.content
            }

            val merge = DevContainerMerger.merge(existingContent, template, extensions)
            val path = existing?.path ?: DevContainerTemplates.DEVCONTAINER_PATH

            val files = buildList {
                add(
                    PreparedFile(
                        path = path,
                        content = merge.merged,
                        commitMessage = "Codespace Manager: code-server-Editor aktivieren",
                    ),
                )
                add(
                    PreparedFile(
                        path = DevContainerTemplates.SETUP_SCRIPT_PATH,
                        content = scriptProvider.setupCodeServerScript(
                            extensions = extensions,
                            editorPort = DevContainerTemplates.EDITOR_PORT,
                        ),
                        commitMessage = "Codespace Manager: Setup-Skript fuer code-server",
                    ),
                )
                add(
                    PreparedFile(
                        path = DevContainerTemplates.EXTENSIONS_TXT_PATH,
                        content = scriptProvider.extensionsText(extensions),
                        commitMessage = "Codespace Manager: Erweiterungsliste (dokumentarisch)",
                    ),
                )
                if (template == RepoTemplate.ANDROID) {
                    add(
                        PreparedFile(
                            path = DevContainerTemplates.ANDROID_SETUP_SCRIPT_PATH,
                            content = scriptProvider.setupAndroidScript(),
                            commitMessage = "Codespace Manager: Android-SDK-Setup",
                        ),
                    )
                }
            }

            val plan = RepoPreparationPlan(
                repoFullName = repo.fullName,
                branch = DevContainerTemplates.EDITOR_BRANCH,
                devcontainerPath = path,
                originalDevcontainer = existingContent,
                devcontainerContent = merge.merged,
                diff = merge.diff,
                files = files,
                notes = merge.notes,
            )
            PrepareRepoResult.NeedsConfirmation(plan)
        } catch (e: Exception) {
            PrepareRepoResult.Failed(e.message ?: "Repo konnte nicht vorbereitet werden")
        }
    }

    /** Legt Branch an (falls noetig) und schreibt die Dateien. */
    suspend fun apply(plan: RepoPreparationPlan): PrepareRepoResult {
        return try {
            val defaultBranch = plan.repoFullName.substringAfter('/', "")
            val existingSha = repoGateway.getBranchSha(plan.repoFullName, plan.branch)
            if (existingSha == null) {
                val sha = repoGateway.getDefaultBranchSha(plan.repoFullName, defaultBranch)
                repoGateway.createBranch(plan.repoFullName, plan.branch, sha)
            }
            plan.files.forEach { file ->
                val existing = repoGateway.getFileContent(plan.repoFullName, file.path, plan.branch)
                repoGateway.putFileContent(
                    fullName = plan.repoFullName,
                    path = file.path,
                    content = file.content,
                    message = file.commitMessage,
                    branch = plan.branch,
                    sha = existing?.sha,
                )
            }
            PrepareRepoResult.Applied(plan)
        } catch (e: Exception) {
            PrepareRepoResult.Failed(e.message ?: "Branch konnte nicht angelegt werden")
        }
    }
}
