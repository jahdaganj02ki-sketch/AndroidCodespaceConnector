package com.codespacemanager.app.domain.usecase

import com.codespacemanager.app.domain.gateway.ExtensionInfo
import com.codespacemanager.app.domain.gateway.OpenVsxGateway
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/**
 * Loest die Erweiterungsliste gegen die Open-VSX-API auf und erzeugt die Lock-Datei
 * (ID -> .vsix-URL) als Fallback, falls die Gallery im Container nicht erreichbar ist.
 */
class SyncExtensionsUseCase @Inject constructor(
    private val openVsxGateway: OpenVsxGateway,
) {

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    suspend fun resolveAll(extensionIds: List<String>): List<ExtensionInfo> =
        extensionIds.mapNotNull { openVsxGateway.resolve(it) }

    /** Lock-Datei: `{ "publisher.name": "https://.../name.vsix" }`. */
    suspend fun buildLockFile(extensionIds: List<String>): String {
        val entries = resolveAll(extensionIds)
            .filter { !it.vsixUrl.isNullOrBlank() }
            .associate { it.id to it.vsixUrl.orEmpty() }
        val obj = JsonObject(entries.mapValues { JsonPrimitive(it.value) })
        return json.encodeToString(JsonObject.serializer(), obj) + "\n"
    }
}
