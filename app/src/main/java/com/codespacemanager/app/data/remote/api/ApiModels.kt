package com.codespacemanager.app.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val login: String,
    val id: Long = 0,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val name: String? = null,
    val email: String? = null,
)

@Serializable
data class OAuthTokenRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    val code: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("code_verifier") val codeVerifier: String,
)

@Serializable
data class OAuthTokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
data class OwnerDto(
    val login: String,
    val id: Long = 0,
    val type: String = "User",
)

@Serializable
data class RepoDto(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: OwnerDto? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val description: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    @SerialName("private") val isPrivate: Boolean = true,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    @SerialName("has_projects") val hasProjects: Boolean = false,
    @SerialName("has_wiki") val hasWiki: Boolean = false,
    val auto_init: Boolean = true,
)

@Serializable
data class ContentDto(
    val path: String = "",
    val sha: String = "",
    val content: String = "",
    val encoding: String? = "base64",
    val size: Int = 0,
) {
    fun decodedContent(): String {
        if (content.isEmpty()) return ""
        if (encoding == null || encoding == "base64") {
            return runCatching { String(java.util.Base64.getDecoder().decode(content.replace("\n", ""))) }
                .getOrDefault(content)
        }
        return content
    }
}

@Serializable
data class PutContentRequest(
    val message: String,
    val content: String,
    val branch: String,
    val sha: String? = null,
)

@Serializable
data class CommitRefDto(
    val sha: String = "",
)

@Serializable
data class CreateRefRequest(
    val ref: String,
    val sha: String,
)

@Serializable
data class GitRefDto(
    val ref: String = "",
    @SerialName("object") val obj: CommitRefDto? = null,
)

@Serializable
data class DevContainerDto(
    val path: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class MachineDto(
    val name: String,
    @SerialName("display_name") val displayName: String? = null,
    val cores: Int? = null,
    @SerialName("memory_gb") val memoryGb: Int? = null,
    val os: String? = null,
)

@Serializable
data class PermissionsCheckDto(
    @SerialName("can_create_codespaces") val canCreateCodespaces: Boolean = true,
    val reason: String? = null,
    @SerialName("allowed_devcontainers") val allowedDevcontainers: List<String> = emptyList(),
)

@Serializable
data class GitStatusDto(
    val ref: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    @SerialName("has_uncommitted_changes") val hasUncommittedChanges: Boolean = false,
    @SerialName("has_unpushed_commits") val hasUnpushedCommits: Boolean = false,
)

@Serializable
data class RuntimeConstraintsDto(
    @SerialName("allowed_port_privacy_settings") val allowedPortPrivacySettings: List<String> = emptyList(),
)

@Serializable
data class CodespaceDto(
    val name: String,
    @SerialName("display_name") val displayName: String? = null,
    val state: String? = null,
    val machine: String? = null,
    val location: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
    @SerialName("devcontainer_path") val devcontainerPath: String? = null,
    @SerialName("git_status") val gitStatus: GitStatusDto? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("billable_owner") val billableOwner: String? = null,
    @SerialName("runtime_constraints") val runtimeConstraints: RuntimeConstraintsDto? = null,
    val repository: RepositoryRefDto? = null,
    val connection: CodespaceConnectionDto? = null,
)

@Serializable
data class RepositoryRefDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
)

@Serializable
data class CreateCodespaceRequest(
    @SerialName("repository_id") val repositoryId: Long,
    val ref: String,
    val machine: String? = null,
    val geo: String? = null,
    val location: String? = null,
    @SerialName("idle_timeout_minutes") val idleTimeoutMinutes: Int? = null,
    @SerialName("devcontainer_path") val devcontainerPath: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("multi_repo_permissions_opt_out") val multiRepoPermissionsOptOut: Boolean = false,
)

@Serializable
data class UpdateCodespaceRequest(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("idle_timeout_minutes") val idleTimeoutMinutes: Int? = null,
    val machine: String? = null,
)

@Serializable
data class TunnelPropertiesDto(
    @SerialName("serviceUri") val serviceUri: String = "",
    @SerialName("tunnelId") val tunnelId: String = "",
    @SerialName("clusterId") val clusterId: String? = null,
    val domain: String? = null,
    @SerialName("connectAccessToken") val connectAccessToken: String? = null,
    @SerialName("managePortsAccessToken") val managePortsAccessToken: String? = null,
)

@Serializable
data class CodespaceConnectionDto(
    @SerialName("tunnelProperties") val tunnelProperties: TunnelPropertiesDto? = null,
)
