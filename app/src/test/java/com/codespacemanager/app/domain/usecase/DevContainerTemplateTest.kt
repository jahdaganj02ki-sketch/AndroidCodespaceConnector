package com.codespacemanager.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class DevContainerTemplateTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(content: String): JsonObject = json.parseToJsonElement(content).jsonObject

    @Test
    fun `standard template enthaelt code-server feature mit den erwarteten optionen`() {
        val content = DevContainerTemplates.render(
            com.codespacemanager.app.domain.model.RepoTemplate.STANDARD,
            DevContainerTemplates.DEFAULT_EXTENSIONS,
        )
        val root = parse(content)
        val features = root.getValue("features").jsonObject
        val options = features.getValue(DevContainerTemplates.CODE_SERVER_FEATURE).jsonObject

        assertThat(options.getValue("version").jsonPrimitive.content)
            .isEqualTo(DevContainerTemplates.CODE_SERVER_VERSION)
        assertThat(options.getValue("auth").jsonPrimitive.content).isEqualTo("none")
        assertThat(options.getValue("host").jsonPrimitive.content).isEqualTo("0.0.0.0")
        assertThat(options.getValue("port").jsonPrimitive.content).isEqualTo("13337")
        assertThat(options.getValue("workspace").jsonPrimitive.content)
            .isEqualTo("/workspaces/\${localWorkspaceFolderBasename}")
        assertThat(options.getValue("disableTelemetry").jsonPrimitive.content).isEqualTo("true")
        assertThat(options.getValue("disableUpdateCheck").jsonPrimitive.content).isEqualTo("true")
        assertThat(options.getValue("disableWorkspaceTrust").jsonPrimitive.content).isEqualTo("true")
        assertThat(options.getValue("extensions").jsonPrimitive.content)
            .isEqualTo("kilocode.kilo-code,saoudrizwan.claude-dev,GitHub.github-vscode-theme")
    }

    @Test
    fun `standard template enthaelt git und github cli sowie den editor port`() {
        val root = parse(
            DevContainerTemplates.render(
                com.codespacemanager.app.domain.model.RepoTemplate.STANDARD,
                DevContainerTemplates.DEFAULT_EXTENSIONS,
            ),
        )
        val features = root.getValue("features").jsonObject
        assertThat(features).containsKey(DevContainerTemplates.GIT_FEATURE)
        assertThat(features).containsKey(DevContainerTemplates.GITHUB_CLI_FEATURE)

        val ports = root.getValue("forwardPorts")
        assertThat(ports.toString()).contains("13337")
        assertThat(root.getValue("portsAttributes").jsonObject).containsKey("13337")
        assertThat(root.getValue("onCreateCommand").jsonPrimitive.content)
            .isEqualTo("bash .devcontainer/setup-code-server.sh")
    }

    @Test
    fun `android template ergaenzt jdk 17 und android sdk`() {
        val root = parse(
            DevContainerTemplates.render(
                com.codespacemanager.app.domain.model.RepoTemplate.ANDROID,
                DevContainerTemplates.DEFAULT_EXTENSIONS,
            ),
        )
        val features = root.getValue("features").jsonObject
        val java = features.getValue(DevContainerTemplates.JAVA_FEATURE).jsonObject
        assertThat(java.getValue("version").jsonPrimitive.content).isEqualTo("17")
        assertThat(root.getValue("postCreateCommand").jsonPrimitive.content)
            .isEqualTo("bash .devcontainer/setup-android.sh")
        assertThat(root.getValue("containerEnv").jsonObject.getValue("ANDROID_HOME").jsonPrimitive.content)
            .isEqualTo("/opt/android-sdk")
    }

    @Test
    fun `leere erweiterungsliste erzeugt keine extensions option`() {
        val root = parse(
            DevContainerTemplates.render(
                com.codespacemanager.app.domain.model.RepoTemplate.STANDARD,
                emptyList(),
            ),
        )
        val options = root.getValue("features").jsonObject
            .getValue(DevContainerTemplates.CODE_SERVER_FEATURE).jsonObject
        assertThat(options).doesNotContainKey("extensions")
    }
}
