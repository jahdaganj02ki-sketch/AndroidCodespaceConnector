package com.codespacemanager.app.data.repository

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.data.remote.api.ApiMappers
import com.codespacemanager.app.data.remote.api.CodespaceApi
import com.codespacemanager.app.data.remote.api.CreateCodespaceRequest
import com.codespacemanager.app.data.remote.api.UpdateCodespaceRequest
import com.codespacemanager.app.data.remote.api.apiCall
import com.codespacemanager.app.domain.gateway.CodespaceGateway
import com.codespacemanager.app.domain.gateway.TunnelProperties
import com.codespacemanager.app.domain.model.Codespace
import com.codespacemanager.app.domain.model.Geo
import javax.inject.Inject
import javax.inject.Singleton

/** Implementierung des [CodespaceGateway] auf Basis der GitHub-REST-API. */
@Singleton
class CodespaceRepositoryImpl @Inject constructor(
    private val codespaceApi: CodespaceApi,
) : CodespaceGateway {

    override suspend fun listCodespaces(): List<Codespace> = apiCall {
        codespaceApi.listCodespaces().map { ApiMappers.toCodespace(it) }
    }

    override suspend fun listRepoCodespaces(repoFullName: String): List<Codespace> = apiCall {
        val (owner, repo) = repoFullName.split("/", limit = 2)
        codespaceApi.listRepoCodespaces(owner, repo).map { ApiMappers.toCodespace(it) }
    }

    override suspend fun getCodespace(name: String): Codespace = apiCall {
        ApiMappers.toCodespace(codespaceApi.getCodespace(name))
    }

    override suspend fun createCodespace(
        repositoryId: Long,
        ref: String,
        machine: String?,
        geo: Geo?,
        location: String?,
        idleTimeoutMinutes: Int?,
        devcontainerPath: String?,
        displayName: String?,
    ): Codespace = apiCall {
        val base = CreateCodespaceRequest(
            repositoryId = repositoryId,
            ref = ref,
            machine = machine,
            geo = geo?.apiValue,
            location = location,
            idleTimeoutMinutes = idleTimeoutMinutes,
            devcontainerPath = devcontainerPath,
            displayName = displayName,
        )
        val created = try {
            codespaceApi.createCodespace(base)
        } catch (e: ApiException) {
            // `geo` wird von GitHub abgeschaltet -> Fallback auf `location`.
            if (e.statusCode == 400 && base.geo != null) {
                codespaceApi.createCodespace(base.copy(geo = null, location = location ?: base.geo))
            } else {
                throw e
            }
        }
        ApiMappers.toCodespace(created)
    }

    override suspend fun startCodespace(name: String) = apiCall {
        codespaceApi.startCodespace(name)
        Unit
    }

    override suspend fun stopCodespace(name: String) = apiCall {
        codespaceApi.stopCodespace(name)
        Unit
    }

    override suspend fun renameCodespace(name: String, displayName: String) = apiCall {
        codespaceApi.updateCodespace(name, UpdateCodespaceRequest(displayName = displayName))
        Unit
    }

    override suspend fun deleteCodespace(name: String) = apiCall {
        codespaceApi.deleteCodespace(name)
        Unit
    }

    override suspend fun tunnelProperties(name: String): TunnelProperties = apiCall {
        val dto = codespaceApi.getCodespace(name, internal = true, refresh = true)
        val properties = dto.connection?.tunnelProperties
            ?: throw ApiException(404, "Keine Tunnel-Credentials fuer $name")
        TunnelProperties(
            serviceUri = properties.serviceUri,
            tunnelId = properties.tunnelId,
            clusterId = properties.clusterId,
            domain = properties.domain,
            connectAccessToken = properties.connectAccessToken,
            managePortsAccessToken = properties.managePortsAccessToken,
        )
    }
}
