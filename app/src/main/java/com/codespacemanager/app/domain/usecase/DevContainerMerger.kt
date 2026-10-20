package com.codespacemanager.app.domain.usecase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Ergebnis eines devcontainer-Merges. */
data class DevContainerMergeResult(
    val original: String,
    val merged: String,
    val changed: Boolean,
    val diff: String,
    val notes: List<String> = emptyList(),
)

/**
 * Merge-Regeln (Plan §5.2):
 *  * `features`: code-server-Feature ergaenzen; falls bereits vorhanden, nur
 *    `auth/host/port/workspace/disable*` ueberschreiben – die `version` des Users bleibt.
 *  * `forwardPorts`: `13337` ergaenzen, Duplikate raus, fremde Ports bleiben.
 *  * `portsAttributes["13337"]`: ergaenzen, fremde Eintraege bleiben.
 *  * `onCreateCommand`: nie ueberschreiben, eigenes Skript vorne einhaengen.
 *  * `image`, fremde `features`, `customizations`, `mounts`, `env`, `ports` bleiben unveraendert.
 */
object DevContainerMerger {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        isLenient = true
    }

    private val codeServerOverwritableOptions = setOf(
        "auth",
        "host",
        "port",
        "workspace",
        "disableTelemetry",
        "disableUpdateCheck",
        "disableWorkspaceTrust",
    )

    /**
     * @param existingJson bestehender Inhalt der devcontainer.json oder `null`
     * @param template Vorlage, wenn kein devcontainer vorhanden ist
     */
    fun merge(
        existingJson: String?,
        template: com.codespacemanager.app.domain.model.RepoTemplate,
        extensions: List<String>,
    ): DevContainerMergeResult {
        val notes = mutableListOf<String>()
        if (existingJson.isNullOrBlank()) {
            val rendered = DevContainerTemplates.render(template, extensions)
            return DevContainerMergeResult(
                original = "",
                merged = rendered,
                changed = true,
                diff = TextDiff.unified("", rendered, "devcontainer.json"),
                notes = notes,
            )
        }

        val root = try {
            json.parseToJsonElement(Jsonc.sanitize(existingJson)) as? JsonObject
                ?: throw IllegalArgumentException("devcontainer.json enthaelt kein JSON-Objekt")
        } catch (e: Exception) {
            throw IllegalArgumentException("devcontainer.json ist kein gueltiges JSON: ${e.message}", e)
        }

        val merged = buildJsonObject {
            root.forEach { (key, value) -> put(key, value) }

            // features
            val featuresElement = root["features"]
            val features = when {
                featuresElement == null -> buildJsonObject {}
                featuresElement is JsonObject -> featuresElement
                else -> {
                    notes += "Feld 'features' hat ein unerwartetes Format und bleibt unveraendert."
                    featuresElement
                }
            }
            if (features is JsonObject) {
                put("features", mergeFeatures(features, extensions))
            } else {
                put("features", features)
            }

            // forwardPorts
            val forwardPorts = root["forwardPorts"]
            when (forwardPorts) {
                null -> put(
                    "forwardPorts",
                    buildJsonArray { add(JsonPrimitive(DevContainerTemplates.EDITOR_PORT)) },
                )

                is JsonArray -> put(
                    "forwardPorts",
                    buildJsonArray {
                        val seen = mutableSetOf<Int>()
                        forwardPorts.forEach { element ->
                            val port = element.jsonPrimitive.content.toIntOrNull()
                            if (port != null) {
                                if (seen.add(port)) add(JsonPrimitive(port))
                            } else {
                                add(element)
                            }
                        }
                        if (seen.add(DevContainerTemplates.EDITOR_PORT)) {
                            add(JsonPrimitive(DevContainerTemplates.EDITOR_PORT))
                        }
                    },
                )

                else -> notes += "Feld 'forwardPorts' hat ein unerwartetes Format und bleibt unveraendert."
            }

            // portsAttributes
            val attributes = root["portsAttributes"]
            when (attributes) {
                null -> put("portsAttributes", defaultPortsAttributes())
                is JsonObject -> {
                    val key = DevContainerTemplates.EDITOR_PORT.toString()
                    val existing = attributes[key]
                    if (existing == null) {
                        put(
                            "portsAttributes",
                            JsonObject(attributes.toMutableMap().apply {
                                put(key, defaultPortsAttributesElement())
                            }),
                        )
                    }
                }

                else -> notes += "Feld 'portsAttributes' hat ein unerwartetes Format und bleibt unveraendert."
            }

            // onCreateCommand
            val setupCommand = "bash ${DevContainerTemplates.SETUP_SCRIPT_PATH}"
            when (val command = root["onCreateCommand"]) {
                null -> put("onCreateCommand", setupCommand)
                is JsonPrimitive -> {
                    if (command.content != setupCommand) {
                        put("onCreateCommand", buildJsonArray {
                            add(JsonPrimitive(setupCommand))
                            add(command)
                        })
                    }
                }

                is JsonArray -> {
                    val alreadyPresent = command.any {
                        it is JsonPrimitive && it.content == setupCommand
                    }
                    if (!alreadyPresent) {
                        put("onCreateCommand", buildJsonArray {
                            add(JsonPrimitive(setupCommand))
                            command.forEach { add(it) }
                        })
                    }
                }

                else -> {
                    put("onCreateCommand", buildJsonArray {
                        add(JsonPrimitive(setupCommand))
                        add(command)
                    })
                }
            }
        }

        val mergedText = json.encodeToString(JsonObject.serializer(), merged) + "\n"
        val changed = normalizeForCompare(mergedText) != normalizeForCompare(existingJson)
        return DevContainerMergeResult(
            original = existingJson,
            merged = mergedText,
            changed = changed,
            diff = TextDiff.unified(existingJson, mergedText, DevContainerTemplates.DEVCONTAINER_PATH),
            notes = notes,
        )
    }

    private fun mergeFeatures(features: JsonObject, extensions: List<String>): JsonObject {
        val result = features.toMutableMap()
        val existing = features[DevContainerTemplates.CODE_SERVER_FEATURE]
        if (existing is JsonObject) {
            val options = existing.toMutableMap()
            DevContainerTemplates.codeServerOptions(extensions).forEach { (key, value) ->
                if (key in codeServerOverwritableOptions) {
                    options[key] = JsonPrimitive(value)
                }
            }
            result[DevContainerTemplates.CODE_SERVER_FEATURE] = JsonObject(options)
        } else {
            result[DevContainerTemplates.CODE_SERVER_FEATURE] = buildJsonObject {
                DevContainerTemplates.codeServerOptions(extensions).forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
        return JsonObject(result)
    }

    private fun defaultPortsAttributes(): JsonObject = buildJsonObject {
        put(DevContainerTemplates.EDITOR_PORT.toString(), defaultPortsAttributesElement())
    }

    private fun defaultPortsAttributesElement(): JsonObject = buildJsonObject {
        put("label", "Editor")
        put("onAutoForward", "silent")
        put("protocol", "http")
    }

    private fun normalizeForCompare(text: String): String = Jsonc.sanitize(text).trim()
}

/** Hilfsfunktionen rund um JSON-Elemente. */
internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.stringFor(key: String): String? = this[key]?.asStringOrNull()

internal fun JsonObject.objectFor(key: String): JsonObject? = this[key]?.asObjectOrNull()

internal fun JsonObject.arrayFor(key: String): JsonArray? = this[key]?.asArrayOrNull()

internal fun JsonObject.intFor(key: String): Int? = this[key]?.jsonPrimitive?.content?.toIntOrNull()
