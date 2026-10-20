package com.codespacemanager.app.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** `https://github.com` – OAuth-Tausch. */
interface AuthApi {
    @POST("login/oauth/access_token")
    suspend fun exchangeToken(@Body body: OAuthTokenRequest): OAuthTokenDto
}

/** `https://api.github.com` – Benutzer und Organisationen. */
interface UserApi {
    @GET("user")
    suspend fun getUser(): UserDto

    @GET("user/orgs")
    suspend fun listOrganizations(): List<OrgDto>
}

@kotlinx.serialization.Serializable
data class OrgDto(
    val login: String,
    val id: Long = 0,
)

/** `https://api.github.com` – Repositories, Contents, Git-Refs, Codespace-Hilfen. */
interface RepoApi {
    @GET("user/repos")
    suspend fun listUserRepos(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
    ): Response<List<RepoDto>>

    @GET("orgs/{org}/repos")
    suspend fun listOrgRepos(
        @Path("org") org: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
    ): Response<List<RepoDto>>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): RepoDto

    @POST("user/repos")
    suspend fun createRepo(@Body body: CreateRepoRequest): RepoDto

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null,
    ): Response<ContentDto>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: PutContentRequest,
    ): Response<ContentDto>

    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "ref", encoded = true) ref: String,
    ): CommitDto

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "branch", encoded = true) branch: String,
    ): Response<GitRefDto>

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateRefRequest,
    ): GitRefDto

    @GET("repositories/{repoId}/codespaces/devcontainers")
    suspend fun listDevContainers(
        @Path("repoId") repoId: Long,
        @Query("ref") ref: String? = null,
    ): List<DevContainerDto>

    @GET("repositories/{repoId}/codespaces/machines")
    suspend fun listMachines(
        @Path("repoId") repoId: Long,
        @Query("location") location: String? = null,
        @Query("ref") ref: String? = null,
        @Query("devcontainer_path") devcontainerPath: String? = null,
    ): List<MachineDto>

    @GET("repositories/{repoId}/codespaces/permissions_check")
    suspend fun checkPermissions(
        @Path("repoId") repoId: Long,
        @Query("ref") ref: String? = null,
        @Query("devcontainer_path") devcontainerPath: String? = null,
    ): PermissionsCheckDto
}

@kotlinx.serialization.Serializable
data class CommitDto(
    val sha: String = "",
)

/** Anlegen eines Repositories in einer Organisation. */
interface OrgRepoApi {
    @POST("orgs/{org}/repos")
    suspend fun createRepo(
        @Path("org") org: String,
        @Body body: CreateRepoRequest,
    ): RepoDto
}

/** `https://api.github.com` – Codespaces. */
interface CodespaceApi {
    @GET("user/codespaces")
    suspend fun listCodespaces(): List<CodespaceDto>

    @GET("repos/{owner}/{repo}/codespaces")
    suspend fun listRepoCodespaces(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<CodespaceDto>

    @GET("user/codespaces/{name}")
    suspend fun getCodespace(
        @Path("name") name: String,
        @Query("internal") internal: Boolean? = null,
        @Query("refresh") refresh: Boolean? = null,
    ): CodespaceDto

    @POST("user/codespaces")
    suspend fun createCodespace(@Body body: CreateCodespaceRequest): CodespaceDto

    @POST("user/codespaces/{name}/start")
    suspend fun startCodespace(@Path("name") name: String)

    @POST("user/codespaces/{name}/stop")
    suspend fun stopCodespace(@Path("name") name: String)

    @PATCH("user/codespaces/{name}")
    suspend fun updateCodespace(
        @Path("name") name: String,
        @Body body: UpdateCodespaceRequest,
    ): CodespaceDto

    @DELETE("user/codespaces/{name}")
    suspend fun deleteCodespace(@Path("name") name: String)
}
