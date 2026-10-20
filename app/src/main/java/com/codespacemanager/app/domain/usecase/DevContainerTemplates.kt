package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.domain.model.RepoTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Rendert eine `.devcontainer/devcontainer.json` für ein neues Repository.
 *
 * Die Feature-Optionen sind verbindlich (Stand Plan §5.2):
 * code-server ohne Zertifikat (reines HTTP), Bind auf 0.0.0.0, Port 13337,
 * Workspace `/workspaces/<repo>`, Telemetrie/Update-Check aus, Workspace-Trust aus.
 */
object DevContainerTemplates {

    const val CODE_SERVER_FEATURE = "ghcr.io/coder/devcontainer-features/code-server:2"
    const val CODE_SERVER_VERSION = "4.141.0"
    const val GIT_FEATURE = "ghcr.io/devcontainers/features/git:1"
    const val GITHUB_CLI_FEATURE = "ghcr.io/devcontainers/features/github-cli:1"
    const val JAVA_FEATURE = "ghcr.io/devcontainers/features/java:1"

    const val EDITOR_PORT = 13337
    const val EDITOR_BRANCH = "codespace/editor"
    const val DEVCONTAINER_PATH = ".devcontainer/devcontainer.json"
    const val SETUP_SCRIPT_PATH = ".devcontainer/setup-code-server.sh"
    const val ANDROID_SETUP_SCRIPT_PATH = ".devcontainer/setup-android.sh"
    const val EXTENSIONS_TXT_PATH = ".devcontainer/extensions.txt"
    const val EXTENSIONS_LOCK_PATH = ".devcontainer/extensions.lock.json"

    const val BASE_IMAGE = "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"

    val DEFAULT_EXTENSIONS = listOf(
        "kilocode.kilo-code",
        "saoudrizwan.claude-dev",
        "GitHub.github-vscode-theme",
    )

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    /** Feature-Optionen des code-server-Features. */
    fun codeServerOptions(extensions: List<String>): Map<String, String> = buildMap {
        put("version", CODE_SERVER_VERSION)
        put("auth", "none")
        put("host", "0.0.0.0")
        put("port", EDITOR_PORT.toString())
        put("workspace", "/workspaces/\${localWorkspaceFolderBasename}")
        put("disableTelemetry", "true")
        put("disableUpdateCheck", "true")
        put("disableWorkspaceTrust", "true")
        val ids = extensions.map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isNotEmpty()) put("extensions", ids.joinToString(","))
    }

    /** Vollständige devcontainer.json für ein neues Repository. */
    fun render(template: RepoTemplate, extensions: List<String>): String {
        val root = buildJsonObject {
            put(
                "name",
                when (template) {
                    RepoTemplate.STANDARD -> "Codespace Manager (Standard)"
                    RepoTemplate.ANDROID -> "Codespace Manager (Android)"
                },
            )
            put("image", BASE_IMAGE)

            putJsonObject("features") {
                putJsonObject(CODE_SERVER_FEATURE) {
                    codeServerOptions(extensions).forEach { (key, value) -> put(key, value) }
                }
                putJsonObject(GIT_FEATURE) {}
                putJsonObject(GITHUB_CLI_FEATURE) {}
                if (template == RepoTemplate.ANDROID) {
                    putJsonObject(JAVA_FEATURE) {
                        put("version", "17")
                    }
                }
            }

            putJsonArray("forwardPorts") { add(JsonPrimitive(EDITOR_PORT)) }

            putJsonObject("portsAttributes") {
                putJsonObject(EDITOR_PORT.toString()) {
                    put("label", "Editor")
                    put("onAutoForward", "silent")
                    put("protocol", "http")
                }
            }

            put("onCreateCommand", "bash $SETUP_SCRIPT_PATH")

            if (template == RepoTemplate.ANDROID) {
                put("postCreateCommand", "bash $ANDROID_SETUP_SCRIPT_PATH")
                putJsonObject("containerEnv") {
                    put("ANDROID_HOME", "/opt/android-sdk")
                    put("ANDROID_SDK_ROOT", "/opt/android-sdk")
                }
            }
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root) + "\n"
    }
}
