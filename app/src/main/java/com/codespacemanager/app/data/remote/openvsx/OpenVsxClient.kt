package com.codespacemanager.app.data.remote.openvsx

import com.codespacemanager.app.domain.gateway.ExtensionInfo
import com.codespacemanager.app.domain.gateway.OpenVsxGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OpenVsxFileDto(
    val download: String = "",
    val checksum: String? = null,
    val signature: String? = null,
)

@Serializable
data class OpenVsxDto(
    val namespace: String = "",
    val name: String = "",
    val version: String = "",
    val files: Map<String, OpenVsxFileDto> = emptyMap(),
    @SerialName("allVersions") val allVersions: Map<String, kotlinx.serialization.json.JsonObject> = emptyMap(),
)

/** Loest Erweiterungen ueber die Open-VSX-API auf (Version + .vsix-URL). */
@Singleton
class OpenVsxClient @Inject constructor(
    private val httpClient: OkHttpClient,
) : OpenVsxGateway {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun resolve(extensionId: String): ExtensionInfo? {
        val parts = extensionId.split(".", limit = 2)
        if (parts.size != 2) return null
        val (namespace, name) = parts
        val request = Request.Builder()
            .url("https://open-vsx.org/api/$namespace/$name")
            .header("Accept", "application/json")
            .build()
        val response = try {
            withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        } catch (e: IOException) {
            return null
        }
        val text = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) return null
        val dto = runCatching { json.decodeFromString(OpenVsxDto.serializer(), text) }.getOrNull() ?: return null
        val url = preferredDownload(dto)
        return ExtensionInfo(
            id = extensionId,
            version = dto.version,
            vsixUrl = url,
        )
    }

    private fun preferredDownload(dto: OpenVsxDto): String? {
        val preferred = listOf("linux-x64", "linux-arm64", "linux-armhf", "universal")
        preferred.forEach { target ->
            dto.files[target]?.download?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return dto.files.values.firstOrNull()?.download?.takeIf { it.isNotBlank() }
    }
}
