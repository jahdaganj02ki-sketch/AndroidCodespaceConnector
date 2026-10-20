package com.codespacemanager.app.data.remote.api

import com.codespacemanager.app.domain.model.Account
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.CodespaceState
import com.codespacemanager.app.domain.model.DevContainerInfo
import com.codespacemanager.app.domain.model.FileContent
import com.codespacemanager.app.domain.model.GitStatus
import com.codespacemanager.app.domain.model.Machine
import com.codespacemanager.app.domain.model.PermissionsCheck
import com.codespacemanager.app.domain.model.Repo
import java.time.Instant
import java.time.format.DateTimeParseException

/** Abbildung der GitHub-DTOs auf die Domain-Modelle. */
object ApiMappers {

    fun toAccount(user: UserDto, scopes: List<String>): Account = Account(
        id = user.login.lowercase(),
        login = user.login,
        avatarUrl = user.avatarUrl,
        scopes = scopes,
        createdAt = System.currentTimeMillis(),
    )

    fun toRepo(dto: RepoDto): Repo = Repo(
        id = dto.id,
        name = dto.name,
        fullName = dto.fullName,
        owner = dto.owner?.login.orEmpty(),
        ownerType = dto.owner?.type ?: "User",
        isPrivate = dto.isPrivate,
        isFork = dto.fork,
        archived = dto.archived,
        defaultBranch = dto.defaultBranch.ifBlank { "main" },
        description = dto.description,
        updatedAt = dto.updatedAt,
    )

    fun toDevContainer(dto: DevContainerDto): DevContainerInfo =
        DevContainerInfo(path = dto.path, displayName = dto.displayName)

    fun toMachine(dto: MachineDto): Machine = Machine(
        name = dto.name,
        displayName = dto.displayName,
        cores = dto.cores,
        memoryGb = dto.memoryGb,
        os = dto.os,
    )

    fun toPermissions(dto: PermissionsCheckDto): PermissionsCheck = PermissionsCheck(
        canCreateCodespaces = dto.canCreateCodespaces,
        reason = dto.reason,
        allowedDevcontainers = dto.allowedDevcontainers,
    )

    fun toFileContent(dto: ContentDto): FileContent = FileContent(
        path = dto.path,
        sha = dto.sha,
        content = dto.decodedContent(),
        encoding = dto.encoding ?: "base64",
    )

    fun toCodespace(dto: CodespaceDto): Codespace = Codespace(
        name = dto.name,
        displayName = dto.displayName,
        repositoryFullName = dto.repository?.fullName.orEmpty(),
        state = CodespaceState.from(dto.state),
        machine = dto.machine,
        location = dto.location,
        webUrl = dto.webUrl,
        devcontainerPath = dto.devcontainerPath,
        gitStatus = dto.gitStatus?.let {
            GitStatus(
                ref = it.ref,
                ahead = it.ahead,
                behind = it.behind,
                hasUncommittedChanges = it.hasUncommittedChanges,
                hasUnpushedCommits = it.hasUnpushedCommits,
            )
        },
        lastUsedAtMillis = toEpochMillis(dto.lastUsedAt),
        createdAtMillis = toEpochMillis(dto.createdAt),
        billableOwner = dto.billableOwner,
        allowedPortPrivacySettings = dto.runtimeConstraints?.allowedPortPrivacySettings.orEmpty(),
    )

    fun toEpochMillis(raw: String?): Long? = raw?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
