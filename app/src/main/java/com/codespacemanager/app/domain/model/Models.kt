package com.codespacemanager.app.domain.model

/** Ein GitHub-Konto, mit dem die App verbunden ist. */
data class Account(
    val id: String,
    val login: String,
    val avatarUrl: String,
    val scopes: List<String>,
    val createdAt: Long,
)

/** Ein Repository (eigenes oder eines Org). */
data class Repo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val ownerType: String,
    val isPrivate: Boolean,
    val isFork: Boolean,
    val archived: Boolean,
    val defaultBranch: String,
    val description: String? = null,
    val updatedAt: String? = null,
) {
    val isOrganization: Boolean get() = ownerType.equals("organization", ignoreCase = true)
}

/** Git-Status eines Codespaces (aus `git_status` der Codespaces-API). */
data class GitStatus(
    val ref: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val hasUncommittedChanges: Boolean = false,
    val hasUnpushedCommits: Boolean = false,
)

enum class CodespaceState(val raw: String) {
    AVAILABLE("Available"),
    SHUTDOWN("Shutdown"),
    STARTING("Starting"),
    REBUILDING("Rebuilding"),
    SHUTTING_DOWN("ShuttingDown"),
    QUEUED("Queued"),
    UNKNOWN("Unknown");

    val isRunning: Boolean
        get() = this == AVAILABLE || this == STARTING || this == REBUILDING

    companion object {
        fun from(raw: String?): CodespaceState =
            entries.firstOrNull { it.raw.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

data class Codespace(
    val name: String,
    val displayName: String? = null,
    val repositoryFullName: String,
    val state: CodespaceState,
    val machine: String? = null,
    val location: String? = null,
    val webUrl: String? = null,
    val devcontainerPath: String? = null,
    val gitStatus: GitStatus? = null,
    val lastUsedAtMillis: Long? = null,
    val createdAtMillis: Long? = null,
    val billableOwner: String? = null,
    val allowedPortPrivacySettings: List<String> = emptyList(),
    val portPublic: Boolean = false,
) {
    val displayLabel: String get() = displayName?.takeIf { it.isNotBlank() } ?: name
}

/** Verfügbare Maschine aus `GET /repositories/{id}/codespaces/machines`. */
data class Machine(
    val name: String,
    val displayName: String? = null,
    val cores: Int? = null,
    val memoryGb: Int? = null,
    val os: String? = null,
)

/** Devcontainer-Variante eines Repositories. */
data class DevContainerInfo(
    val path: String,
    val displayName: String? = null,
)

/** Ergebnis von `permissions_check`. */
data class PermissionsCheck(
    val canCreateCodespaces: Boolean,
    val reason: String? = null,
    val allowedDevcontainers: List<String> = emptyList(),
)

/** Dateiinhalt über die Contents-API. */
data class FileContent(
    val path: String,
    val sha: String,
    val content: String,
    val encoding: String = "base64",
)

/** Eine Seite von paginierten Ergebnissen. */
data class Page<T>(
    val items: List<T>,
    val nextPage: Int? = null,
)

/** Vorlage für ein neu angelegtes Repository. */
enum class RepoTemplate(val label: String, val description: String) {
    STANDARD("Standard", "Schnellstart: Ubuntu 24.04, git, GitHub CLI und code-server"),
    ANDROID("Android", "Zusätzlich JDK 17 und Android SDK (Plattform 35)"),
}

/** Regionen für `POST /user/codespaces` (`geo`). */
enum class Geo(val apiValue: String, val label: String) {
    EUROPE_WEST("EuropeWest", "Europa (West)"),
    US_EAST("UsEast", "USA (Ost)"),
    US_WEST("UsWest", "USA (West)"),
    SOUTHEAST_ASIA("SoutheastAsia", "Südostasien"),
    ;

    companion object {
        fun from(raw: String?): Geo =
            entries.firstOrNull { it.apiValue.equals(raw?.trim(), ignoreCase = true) }
                ?: EUROPE_WEST
    }
}
