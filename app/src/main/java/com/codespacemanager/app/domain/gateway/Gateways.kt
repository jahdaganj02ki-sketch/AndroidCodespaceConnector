package com.codespacemanager.app.domain.gateway

import com.codespacemanager.app.domain.model.Account
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.DevContainerInfo
import com.codespacemanager.app.domain.model.FileContent
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.model.Machine
import com.codespacemanager.app.domain.model.Page
import com.codespacemanager.app.domain.model.PermissionsCheck
import com.codespacemanager.app.domain.model.Repo

/** Tunnel-Credentials aus `GET /user/codespaces/{name}?internal=true&refresh=true`. */
data class TunnelProperties(
    val serviceUri: String,
    val tunnelId: String,
    val clusterId: String? = null,
    val domain: String? = null,
    val connectAccessToken: String? = null,
    val managePortsAccessToken: String? = null,
) {
    /** `https://use.rel.tunnels.api.visualstudio.com/` -> ohne trailing slash. */
    val normalizedServiceUri: String get() = serviceUri.trimEnd('/')
}

enum class PortVisibility { PUBLIC, PRIVATE }

/** Ergebnis eines Port-Setups. */
data class PortSetupResult(
    val success: Boolean,
    val alreadyInDesiredState: Boolean = false,
    val errorMessage: String? = null,
)

interface AuthGateway {
    suspend fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
        codeVerifier: String,
    ): String

    suspend fun currentUser(token: String): Account
}

interface RepoGateway {
    suspend fun listUserRepos(page: Int): Page<Repo>
    suspend fun listOrganizations(): List<String>
    suspend fun listOrgRepos(org: String, page: Int): Page<Repo>
    suspend fun getRepo(fullName: String): Repo

    suspend fun getDefaultBranchSha(fullName: String, branch: String): String
    suspend fun getBranchSha(fullName: String, branch: String): String?
    suspend fun getFileContent(fullName: String, path: String, ref: String? = null): FileContent?
    suspend fun putFileContent(
        fullName: String,
        path: String,
        content: String,
        message: String,
        branch: String,
        sha: String? = null,
    ): String

    suspend fun createBranch(fullName: String, branch: String, sha: String): Boolean
    suspend fun listDevContainers(repoId: Long, ref: String?): List<DevContainerInfo>
    suspend fun listMachines(
        repoId: Long,
        location: String?,
        ref: String?,
        devcontainerPath: String?,
    ): List<Machine>

    suspend fun checkPermissions(
        repoId: Long,
        ref: String?,
        devcontainerPath: String?,
    ): PermissionsCheck

    suspend fun createRepo(
        name: String,
        isPrivate: Boolean,
        owner: String?,
        description: String?,
    ): Repo
}

interface CodespaceGateway {
    suspend fun listCodespaces(): List<Codespace>
    suspend fun listRepoCodespaces(repoFullName: String): List<Codespace>
    suspend fun getCodespace(name: String): Codespace

    suspend fun createCodespace(
        repositoryId: Long,
        ref: String,
        machine: String?,
        geo: Geo?,
        location: String?,
        idleTimeoutMinutes: Int?,
        devcontainerPath: String?,
        displayName: String?,
    ): Codespace

    suspend fun startCodespace(name: String)
    suspend fun stopCodespace(name: String)
    suspend fun renameCodespace(name: String, displayName: String)
    suspend fun deleteCodespace(name: String)
    suspend fun tunnelProperties(name: String): TunnelProperties
}

interface PortGateway {
    suspend fun getPort(properties: TunnelProperties, port: Int): TunnelPort?
    suspend fun setPort(
        properties: TunnelProperties,
        port: Int,
        visibility: PortVisibility,
    ): PortSetupResult

    suspend fun isPublic(properties: TunnelProperties, port: Int): Boolean
}

/** Repräsentation eines Dev-Tunnel-Ports (Subset der Tunnel-API). */
data class TunnelPort(
    val portNumber: Int,
    val protocol: String = "http",
    val labels: List<String> = emptyList(),
    val anonymousAccess: Boolean = false,
    val organizationDeny: Boolean = false,
)
