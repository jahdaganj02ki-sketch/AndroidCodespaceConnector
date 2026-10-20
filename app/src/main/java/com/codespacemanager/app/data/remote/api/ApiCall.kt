package com.codespacemanager.app.data.remote.api

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.core.NetworkException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

/** Fuehrt einen API-Aufruf aus und wandelt Fehler in [ApiException] um. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    throw e.toApiException(raw)
} catch (e: IOException) {
    throw NetworkException("Netzwerkfehler: ${e.message}", e)
}

fun HttpException.toApiException(raw: String?): ApiException {
    val parsed = raw?.let {
        runCatching { errorJson.parseToJsonElement(it).jsonObject }.getOrNull()
    }
    val message = parsed?.get("message")?.jsonPrimitive?.content
        ?: "GitHub-API antwortete mit HTTP ${code()}"
    val allowPermissionsUrl = parsed?.get("allow_permissions_url")?.jsonPrimitive?.content
    return ApiException(
        statusCode = code(),
        message = message,
        allowPermissionsUrl = allowPermissionsUrl,
        body = raw,
    )
}

/** Wandelt eine Retrofit-Response in den Body oder eine [ApiException]. */
fun <T> retrofit2.Response<T>.requireBody(): T {
    if (!isSuccessful) {
        val raw = runCatching { errorBody()?.string() }.getOrNull()
        throw ApiException(
            statusCode = code(),
            message = "GitHub-API antwortete mit HTTP $code",
            body = raw,
        )
    }
    return body() ?: throw ApiException(code(), "GitHub-API lieferte eine leere Antwort")
}
