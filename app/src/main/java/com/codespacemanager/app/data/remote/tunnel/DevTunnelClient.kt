package com.codespacemanager.app.data.remote.tunnel

import com.codespacemanager.app.core.NetworkException
import com.codespacemanager.app.core.TunnelException
import com.codespacemanager.app.di.TunnelClient
import com.codespacemanager.app.domain.gateway.SettingsGateway
import com.codespacemanager.app.domain.gateway.TunnelProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client fuer die Dev-Tunnels-REST-API (nicht `api.github.com`).
 *
 * Pfadvarianten: je nach SDK-Version liegt die API unter
 * `.../tunnels/{id}/ports` oder `.../api/v1/tunnels/{id}/ports`.
 * Die funktionierende Variante wird in den Einstellungen gemerkt.
 */
@Singleton
class DevTunnelClient @Inject constructor(
    @TunnelClient private val httpClient: OkHttpClient,
    private val settingsGateway: SettingsGateway,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Port lesen (null, wenn der Port nicht existiert). */
    suspend fun getPort(properties: TunnelProperties, port: Int): TunnelPortResponseDto? {
        val response = execute(properties, port, "GET", null)
        if (response.code == 404) return null
        if (!response.isSuccessful) {
            throw TunnelException(response.code, "Port $port konnte nicht gelesen werden")
        }
        val body = response.body
        if (body.isBlank()) return null
        return runCatching { json.decodeFromString(TunnelPortResponseDto.serializer(), body) }.getOrNull()
    }

    /**
     * Port anlegen oder aktualisieren.
     * @return true, wenn der Port danach im gewuenschten Zustand ist.
     */
    suspend fun putPort(
        properties: TunnelProperties,
        port: Int,
        entry: TunnelAccessControlEntryDto,
    ): Boolean {
        val payload = TunnelPortRequestDto(
            portNumber = port,
            protocol = "http",
            labels = listOf("UserForwardedPort"),
            accessControl = TunnelAccessControlDto(listOf(entry)),
        )
        val body = json.encodeToString(TunnelPortRequestDto.serializer(), payload)
            .toRequestBody("application/json;charset=UTF-8".toMediaType())

        // Anlegen (If-None-Match) -> bei 409/412 aktualisieren (If-Match)
        var response = execute(properties, port, "PUT", body, ifNoneMatch = true)
        if (response.code == 409 || response.code == 412) {
            response = execute(properties, port, "PUT", body, ifMatch = true)
        }
        if (response.isSuccessful) return true
        if (response.code == 409 || response.code == 412) {
            // Port existiert bereits; der Zustand wird als gewuenscht akzeptiert.
            return true
        }
        throw TunnelException(response.code, "Port $port konnte nicht gesetzt werden")
    }

    private suspend fun execute(
        properties: TunnelProperties,
        port: Int,
        method: String,
        body: okhttp3.RequestBody?,
        ifNoneMatch: Boolean = false,
        ifMatch: Boolean = false,
    ): TunnelResponse {
        val variants = orderedVariants()
        var lastStatus = 0
        for (variant in variants) {
            val url = buildUrl(properties, variant, port)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Tunnel ${properties.managePortsAccessToken.orEmpty()}")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Accept", "application/json")
                .apply {
                    if (ifNoneMatch) header("If-None-Match", "*")
                    if (ifMatch) header("If-Match", "*")
                }
                .method(method, body)
                .build()
            val response = try {
                withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            } catch (e: IOException) {
                throw NetworkException("Tunnel-API nicht erreichbar: ${e.message}", e)
            }
            val code = response.code
            val text = response.use { it.body?.string().orEmpty() }
            if (code == 404 || code == 405) {
                // Falsche Pfadvariante -> andere probieren
                lastStatus = code
                continue
            }
            if (variant != variants.first()) {
                rememberVariant(variant)
            }
            return TunnelResponse(code = code, body = text)
        }
        throw TunnelException(lastStatus, "Tunnel-API nicht erreichbar (Pfadvariante)")
    }

    private fun buildUrl(properties: TunnelProperties, variant: String, port: Int): String {
        val base = properties.normalizedServiceUri
        val prefix = variant.trim('/')
        return "$base/$prefix/tunnels/${properties.tunnelId}/ports/$port" +
            "?api-version=$API_VERSION"
    }

    private suspend fun orderedVariants(): List<String> {
        val stored = settingsGateway.current().tunnelApiPathVariant
        val known = PATH_VARIANTS.filter { it != stored }
        return if (stored.isBlank()) PATH_VARIANTS else listOf(stored) + known
    }

    private suspend fun rememberVariant(variant: String) {
        settingsGateway.update { it.copy(tunnelApiPathVariant = variant) }
    }

    companion object {
        const val API_VERSION = "2023-09-27-preview"
        val PATH_VARIANTS = listOf("/tunnels", "/api/v1/tunnels")
    }
}

/** Antwort der Tunnel-API nach dem Lesen des Bodys. */
data class TunnelResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean get() = code in 200..299
}
