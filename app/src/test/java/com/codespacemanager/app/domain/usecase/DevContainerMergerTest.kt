package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.domain.model.RepoTemplate
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class DevContainerMergerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    @Test
    fun `ohne devcontainer wird das standard template verwendet`() {
        val result = DevContainerMerger.merge(null, RepoTemplate.STANDARD, listOf("kilocode.kilo-code"))
        assertThat(result.changed).isTrue()
        assertThat(result.merged).contains(DevContainerTemplates.CODE_SERVER_FEATURE)
        assertThat(result.merged).contains("13337")
    }

    @Test
    fun `image und fremde features bleiben unveraendert`() {
        val original = """
            {
              "name": "Mein Container",
              "image": "mcr.microsoft.com/devcontainers/java:17",
              "features": {
                "ghcr.io/devcontainers/features/java:1": { "version": "17" }
              },
              "customizations": { "vscode": { "extensions": ["foo.bar"] } },
              "env": { "FOO": "bar" }
            }
        """.trimIndent()

        val result = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        val root = parse(result.merged)

        assertThat(root.getValue("image").jsonPrimitive.content)
            .isEqualTo("mcr.microsoft.com/devcontainers/java:17")
        assertThat(root.getValue("name").jsonPrimitive.content).isEqualTo("Mein Container")
        assertThat(root.getValue("env").jsonObject.getValue("FOO").jsonPrimitive.content).isEqualTo("bar")
        assertThat(root.getValue("customizations").jsonObject).containsKey("vscode")

        val features = root.getValue("features").jsonObject
        assertThat(features).containsKey(DevContainerTemplates.JAVA_FEATURE)
        assertThat(features).containsKey(DevContainerTemplates.CODE_SERVER_FEATURE)
    }

    @Test
    fun `vorhandene code-server version bleibt unangetastet`() {
        val original = """
            {
              "features": {
                "ghcr.io/coder/devcontainer-features/code-server:2": {
                  "version": "4.100.0",
                  "auth": "password"
                }
              }
            }
        """.trimIndent()

        val result = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        val options = parse(result.merged)
            .getValue("features").jsonObject
            .getValue(DevContainerTemplates.CODE_SERVER_FEATURE).jsonObject

        assertThat(options.getValue("version").jsonPrimitive.content).isEqualTo("4.100.0")
        assertThat(options.getValue("auth").jsonPrimitive.content).isEqualTo("none")
        assertThat(options.getValue("host").jsonPrimitive.content).isEqualTo("0.0.0.0")
        assertThat(options.getValue("port").jsonPrimitive.content).isEqualTo("13337")
    }

    @Test
    fun `onCreateCommand wird nicht ueberschrieben sondern ergaenzt`() {
        val original = """{ "onCreateCommand": "echo hallo" }"""
        val result = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        val command = parse(result.merged).getValue("onCreateCommand")

        assertThat(command).isInstanceOf(JsonArray::class.java)
        val array = command.jsonArray
        assertThat(array).hasSize(2)
        assertThat(array[0].jsonPrimitive.content).isEqualTo("bash .devcontainer/setup-code-server.sh")
        assertThat(array[1].jsonPrimitive.content).isEqualTo("echo hallo")
    }

    @Test
    fun `vorhandenes onCreateCommand array wird ergaenzt nicht verdoppelt`() {
        val original = """{ "onCreateCommand": ["echo a", "echo b"] }"""
        val first = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        val second = DevContainerMerger.merge(first.merged, RepoTemplate.STANDARD, emptyList())
        val array = parse(second.merged).getValue("onCreateCommand").jsonArray

        assertThat(array).hasSize(3)
        assertThat(array[0].jsonPrimitive.content).isEqualTo("bash .devcontainer/setup-code-server.sh")
        assertThat(array[1].jsonPrimitive.content).isEqualTo("echo a")
    }

    @Test
    fun `forwardPorts behalten fremde ports und duplikate werden entfernt`() {
        val original = """{ "forwardPorts": [3000, 13337, 3000] }"""
        val result = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        val ports = parse(result.merged).getValue("forwardPorts").jsonArray.map { it.jsonPrimitive.content.toInt() }

        assertThat(ports).containsExactly(3000, 13337).inOrder()
    }

    @Test
    fun `portsAttributes 13337 wird ergaenzt fremde eintraege bleiben`() {
        val original = """{ "portsAttributes": { "3000": { "label": "Web" } } }"""
        val result = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        val attributes = parse(result.merged).getValue("portsAttributes").jsonObject

        assertThat(attributes).containsKey("3000")
        assertThat(attributes).containsKey("13337")
        assertThat(attributes.getValue("13337").jsonObject.getValue("protocol").jsonPrimitive.content)
            .isEqualTo("http")
    }

    @Test
    fun `kommentare in der devcontainer datei werden toleriert`() {
        val original = """
            {
              // eigenes devcontainer
              "image": "ubuntu:24.04",
              "features": {},
            }
        """.trimIndent()

        val result = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        val root = parse(result.merged)
        assertThat(root.getValue("image").jsonPrimitive.content).isEqualTo("ubuntu:24.04")
        assertThat(root.getValue("features").jsonObject).containsKey(DevContainerTemplates.CODE_SERVER_FEATURE)
    }

    @Test
    fun `unveraenderte datei erzeugt keinen diff`() {
        val merged = DevContainerMerger.merge(null, RepoTemplate.STANDARD, emptyList()).merged
        val second = DevContainerMerger.merge(merged, RepoTemplate.STANDARD, emptyList())
        assertThat(second.changed).isFalse()
        assertThat(second.diff).isEmpty()
    }

    @Test
    fun `diff enthaelt hinzugefuegte zeilen`() {
        val original = """{ "image": "ubuntu:24.04" }"""
        val result = DevContainerMerger.merge(original, RepoTemplate.STANDARD, emptyList())
        assertThat(result.diff).contains("+")
        assertThat(result.diff).contains("features")
    }
}
