package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.domain.gateway.CodespaceGateway
import com.codespacemanager.app.domain.gateway.PortGateway
import com.codespacemanager.app.domain.gateway.PortSetupResult
import com.codespacemanager.app.domain.gateway.PortVisibility
import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.gateway.TunnelPort
import com.codespacemanager.app.domain.gateway.TunnelProperties
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.CodespaceState
import com.codespacemanager.app.domain.model.DevContainerInfo
import com.codespacemanager.app.domain.model.FileContent
import com.codespacemanager.app.domain.model.Machine
import com.codespacemanager.app.domain.model.PermissionsCheck
import com.codespacemanager.app.domain.model.Repo
import com.codespacemanager.app.domain.model.RepoTemplate

/** Test-Doppelgänger für die Gateways. */
class FakeRepoGateway(
    var repo: Repo = Repo(
        id = 1,
        name = "demo",
        fullName = "octo/demo",
        owner = "octo",
        ownerType = "User",
        isPrivate = true,
        isFork = false,
        archived = false,
        defaultBranch = "main",
    ),
    var permissions: PermissionsCheck = PermissionsCheck(canCreateCodespaces = true),
    var devContainers: List<DevContainerInfo> = emptyList(),
    var createError: Throwable? = null,
) : RepoGateway {

    var putFileCount: Int = 0
    var createdBranch: String? = null

    override suspend fun listUserRepos(page: Int) = com.codespacemanager.app.domain.model.Page(emptyList<Repo>())

    override suspend fun listOrganizations(): List<String> = emptyList()

    override suspend fun listOrgRepos(org: String, page: Int) =
        com.codespacemanager.app.domain.model.Page(emptyList<Repo>())

    override suspend fun getRepo(fullName: String): Repo = repo

    override suspend fun getDefaultBranchSha(fullName: String, branch: String): String = "sha-1"

    override suspend fun getBranchSha(fullName: String, branch: String): String? = null

    override suspend fun getFileContent(fullName: String, path: String, ref: String?): FileContent? = null

    override suspend fun putFileContent(
        fullName: String,
        path: String,
        content: String,
        message: String,
        branch: String,
        sha: String?,
    ): String {
        putFileCount++
        return "content-sha"
    }

    override suspend fun createBranch(fullName: String, branch: String, sha: String): Boolean {
        createdBranch = branch
        return true
    }

    override suspend fun listDevContainers(repoId: Long, ref: String?): List<DevContainerInfo> = devContainers

    override suspend fun listMachines(
        repoId: Long,
        location: String?,
        ref: String?,
        devcontainerPath: String?,
    ): List<Machine> = emptyList()

    override suspend fun checkPermissions(
        repoId: Long,
        ref: String?,
        devcontainerPath: String?,
    ): PermissionsCheck = permissions

    override suspend fun createRepo(
        name: String,
        isPrivate: Boolean,
        owner: String?,
        description: String?,
    ): Repo = repo
}

class FakeCodespaceGateway(
    var codespaces: List<Codespace> = emptyList(),
    var createError: Throwable? = null,
    var state: CodespaceState = CodespaceState.AVAILABLE,
) : CodespaceGateway {

    var started = 0
    var stopped = 0
    var deleted = 0
    var created = false

    override suspend fun listCodespaces(): List<Codespace> = codespaces

    override suspend fun listRepoCodespaces(repoFullName: String): List<Codespace> = codespaces

    override suspend fun getCodespace(name: String): Codespace = Codespace(
        name = name,
        repositoryFullName = "octo/demo",
        state = state,
        machine = "basicLinux32gb",
        location = "EuropeWest",
        devcontainerPath = DevContainerTemplates.DEVCONTAINER_PATH,
        lastUsedAtMillis = System.currentTimeMillis(),
    )

    override suspend fun createCodespace(
        repositoryId: Long,
        ref: String,
        machine: String?,
        geo: com.codespacemanager.app.domain.model.Geo?,
        location: String?,
        idleTimeoutMinutes: Int?,
        devcontainerPath: String?,
        displayName: String?,
    ): Codespace {
        createError?.let { throw it }
        created = true
        return Codespace(
            name = "demo-1",
            repositoryFullName = "octo/demo",
            state = CodespaceState.AVAILABLE,
            machine = machine,
            devcontainerPath = devcontainerPath,
        )
    }

    override suspend fun startCodespace(name: String) {
        started++
    }

    override suspend fun stopCodespace(name: String) {
        stopped++
    }

    override suspend fun renameCodespace(name: String, displayName: String) = Unit

    override suspend fun deleteCodespace(name: String) {
        deleted++
    }

    override suspend fun tunnelProperties(name: String): TunnelProperties = TunnelProperties(
        serviceUri = "https://use.rel.tunnels.api.visualstudio.com/",
        tunnelId = "tunnel-1",
        connectAccessToken = "connect",
        managePortsAccessToken = "manage",
    )
}

class FakePortGateway(
    var succeed: Boolean = true,
) : PortGateway {

    var lastVisibility: PortVisibility? = null

    override suspend fun getPort(properties: TunnelProperties, port: Int): TunnelPort? =
        TunnelPort(portNumber = port, anonymousAccess = lastVisibility == PortVisibility.PUBLIC)

    override suspend fun setPort(
        properties: TunnelProperties,
        port: Int,
        visibility: PortVisibility,
    ): PortSetupResult {
        lastVisibility = visibility
        return PortSetupResult(success = succeed)
    }

    override suspend fun isPublic(properties: TunnelProperties, port: Int): Boolean =
        lastVisibility == PortVisibility.PUBLIC
}

class FakeEditorHealth(var reachable: Boolean = true) : EditorHealthGateway {
    var probes = 0
    override suspend fun probe(url: String): EditorProbeResult {
        probes++
        return if (reachable) EditorProbeResult.OK else EditorProbeResult.UNREACHABLE
    }
}

class FakeScriptProvider : DevContainerScriptProvider {
    override suspend fun setupCodeServerScript(extensions: List<String>, editorPort: Int): String =
        "setup-code-server $editorPort ${extensions.joinToString(",")}"

    override suspend fun setupAndroidScript(): String = "setup-android"

    override suspend fun extensionsText(extensions: List<String>): String = extensions.joinToString("\n")
}
