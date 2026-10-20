package com.codespacemanager.app.data.remote.auth

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.data.remote.api.OAuthTokenRequest
import com.codespacemanager.app.data.remote.api.apiCall
import com.codespacemanager.app.domain.gateway.AuthGateway
import com.codespacemanager.app.domain.model.Account
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** OAuth-Login (Authorization Code + PKCE) gegen github.com. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: com.codespacemanager.app.data.remote.api.AuthApi,
    private val httpClient: OkHttpClient,
) : AuthGateway {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
        codeVerifier: String,
    ): String = apiCall {
        val response = authApi.exchangeToken(
            OAuthTokenRequest(
                clientId = clientId,
                clientSecret = clientSecret,
                code = code,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier,
            ),
        )
        response.accessToken
            ?: throw ApiException(
                statusCode = 400,
                message = response.errorDescription ?: response.error ?: "Token-Tausch fehlgeschlagen",
            )
    }

    override suspend fun currentUser(token: String): Account {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(0, "GitHub ist nicht erreichbar: ${e.message}")
        }
        val text = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) {
            throw ApiException(response.code, "Profil konnte nicht geladen werden (HTTP ${response.code})", body = text)
        }
        val element = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw ApiException(0, "Ungueltige Antwort von GitHub")
        val login = element["login"]?.jsonPrimitive?.content
            ?: throw ApiException(0, "Ungueltige Antwort von GitHub")
        val avatar = element["avatar_url"]?.jsonPrimitive?.content.orEmpty()
        return Account(
            id = login.lowercase(),
            login = login,
            avatarUrl = avatar,
            scopes = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
    }

    companion object {
        const val GITHUB_API_VERSION = "2026-03-10"
    }
}
