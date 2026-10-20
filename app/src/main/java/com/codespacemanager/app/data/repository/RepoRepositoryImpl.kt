package com.codespacemanager.app.data.repository

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.data.remote.api.CreateRefRequest
import com.codespacemanager.app.data.remote.api.CreateRepoRequest
import com.codespacemanager.app.data.remote.api.PutContentRequest
import com.codespacemanager.app.data.remote.api.RepoApi
import com.codespacemanager.app.data.remote.api.ApiMappers
import com.codespacemanager.app.data.remote.api.LinkHeader
import com.codespacemanager.app.data.remote.api.apiCall
import com.codespacemanager.app.data.remote.api.requireBody
import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.model.DevContainerInfo
import com.codespacemanager.app.domain.model.FileContent
import com.codespacemanager.app.domain.model.Machine
import com.codespacemanager.app.domain.model.Page
import com.codespacemanager.app.domain.model.PermissionsCheck
import com.codespacemanager.app.domain.model.Repo
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Implementierung des [RepoGateway] auf Basis der GitHub-REST-API. */
@Singleton
class RepoRepositoryImpl @Inject constructor(
    private val repoApi: RepoApi,
    private val orgRepoApi: com.codespacemanager.app.data.remote.api.OrgRepoApi,
    private val userApi: com.codespacemanager.app.data.remote.api.UserApi,
) : RepoGateway {

    override suspend fun listUserRepos(page: Int): Page<Repo> = apiCall {
        val response = repoApi.listUserRepos(page)
        val body = response.requireBody()
        Page(
            items = body.map { ApiMappers.toRepo(it) },
            nextPage = LinkHeader.nextPage(response.headers()["Link"]),
        )
    }

    override suspend fun listOrganizations(): List<String> = apiCall {
        userApi.listOrganizations().map { it.login }
    }

    override suspend fun listOrgRepos(org: String, page: Int): Page<Repo> = apiCall {
        val response = repoApi.listOrgRepos(org, page)
        val body = response.requireBody()
        Page(
            items = body.map { ApiMappers.toRepo(it) },
            nextPage = LinkHeader.nextPage(response.headers()["Link"]),
        )
    }

    override suspend fun getRepo(fullName: String): Repo = apiCall {
        val (owner, repo) = fullName.split("/", limit = 2)
        ApiMappers.toRepo(repoApi.getRepo(owner, repo))
    }

    override suspend fun getDefaultBranchSha(fullName: String, branch: String): String = apiCall {
        val (owner, repo) = fullName.split("/", limit = 2)
        repoApi.getCommit(owner, repo, branch).sha
    }

    override suspend fun getBranchSha(fullName: String, branch: String): String? = apiCall {
        val (owner, repo) = fullName.split("/", limit = 2)
        val response = repoApi.getBranchRef(owner, repo, branch)
        if (response.code() == 404) {
            null
        } else {
            response.requireBody().obj?.sha
        }
    }

    override suspend fun getFileContent(fullName: String, path: String, ref: String?): FileContent? = apiCall {
        val (owner, repo) = fullName.split("/", limit = 2)
        val response = repoApi.getContent(owner, repo, path, ref)
        if (response.code() == 404) {
            null
        } else {
            ApiMappers.toFileContent(response.requireBody())
        }
    }

    override suspend fun putFileContent(
        fullName: String,
        path: String,
        content: String,
        message: String,
        branch: String,
        sha: String?,
    ): String = apiCall {
        val (owner, repo) = fullName.split("/", limit = 2)
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val response = repoApi.putContent(
            owner = owner,
            repo = repo,
            path = path,
            body = PutContentRequest(
                message = message,
                content = encoded,
                branch = branch,
                sha = sha,
            ),
        )
        response.requireBody().sha
    }

    override suspend fun createBranch(fullName: String, branch: String, sha: String): Boolean = apiCall {
        val (owner, repo) = fullName.split("/", limit = 2)
        repoApi.createRef(
            owner = owner,
            repo = repo,
            body = CreateRefRequest(ref = "refs/heads/$branch", sha = sha),
        )
        true
    }

    override suspend fun listDevContainers(repoId: Long, ref: String?): List<DevContainerInfo> = apiCall {
        repoApi.listDevContainers(repoId, ref).map { ApiMappers.toDevContainer(it) }
    }

    override suspend fun listMachines(
        repoId: Long,
        location: String?,
        ref: String?,
        devcontainerPath: String?,
    ): List<Machine> = apiCall {
        repoApi.listMachines(repoId, location, ref, devcontainerPath).map { ApiMappers.toMachine(it) }
    }

    override suspend fun checkPermissions(
        repoId: Long,
        ref: String?,
        devcontainerPath: String?,
    ): PermissionsCheck = apiCall {
        ApiMappers.toPermissions(repoApi.checkPermissions(repoId, ref, devcontainerPath))
    }

    override suspend fun createRepo(
        name: String,
        isPrivate: Boolean,
        owner: String?,
        description: String?,
    ): Repo = apiCall {
        val body = CreateRepoRequest(
            name = name,
            description = description,
            isPrivate = isPrivate,
        )
        val created = if (owner.isNullOrBlank()) {
            repoApi.createRepo(body)
        } else {
            orgRepoApi.createRepo(owner, body)
        }
        ApiMappers.toRepo(created)
    }
}
