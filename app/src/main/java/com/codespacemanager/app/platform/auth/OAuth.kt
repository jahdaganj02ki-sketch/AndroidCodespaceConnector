package com.codespacemanager.app.platform.auth

import com.codespacemanager.app.domain.usecase.Pkce
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/** Anfrage fuer den OAuth-Flow. */
data class OAuthRequest(
    val state: String,
    val codeVerifier: String,
    val redirectUri: String = REDIRECT_URI,
) {
    companion object {
        const val REDIRECT_URI = "com.codespacemanager.app://oauth/callback"

        fun create(random: SecureRandom = SecureRandom()): OAuthRequest = OAuthRequest(
            state = Pkce.generateState(random),
            codeVerifier = Pkce.generateCodeVerifier(random),
        )
    }
}

/**
 * Anmeldeweg. Beide Varianten laufen ueber GitHubs eigene Anmeldeseite (Custom Tab);
 * es steckt kein Google-SDK in der App.
 */
enum class SignInProvider {
    /** GitHub-Kontoauswahl (`prompt=select_account`). */
    GITHUB,

    /**
     * GitHubs volle Anmeldeseite ohne `prompt` – dort blendet GitHub
     * „Continue with Google" / „Continue with Apple" ein.
     */
    GOOGLE,
}

/** Ergebnis des OAuth-Callbacks. */
sealed interface OAuthCallback {
    data class Success(val code: String, val state: String?) : OAuthCallback
    data class Failure(val error: String, val description: String? = null) : OAuthCallback
}

/** Baut die Autorisierungs-URL und wertet den Redirect aus. */
object OAuth {

    private const val AUTHORIZE_URL = "https://github.com/login/oauth/authorize"

    const val SCOPES = "repo codespace read:org"

    fun authorizeUrl(
        clientId: String,
        request: OAuthRequest,
        provider: SignInProvider = SignInProvider.GITHUB,
        login: String? = null,
    ): String {
        val params = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to request.redirectUri,
            "scope" to SCOPES,
            "state" to request.state,
            "code_challenge" to Pkce.codeChallenge(request.codeVerifier),
            "code_challenge_method" to "S256",
        )
        // Ohne "prompt" zeigt GitHub die komplette Anmeldeseite inkl. "Continue with Google".
        if (provider == SignInProvider.GITHUB) {
            params["prompt"] = "select_account"
        }
        if (!login.isNullOrBlank()) {
            params["login"] = login
        }
        return AUTHORIZE_URL + "?" + params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    fun parseCallback(uri: String): OAuthCallback {
        val query = uri.substringAfter('?', "")
        if (query.isBlank()) return OAuthCallback.Failure("missing_code")
        val parameters = parseQuery(query)
        val error = parameters["error"]
        if (!error.isNullOrBlank()) {
            return OAuthCallback.Failure(error, parameters["error_description"])
        }
        val code = parameters["code"]
        if (code.isNullOrBlank()) return OAuthCallback.Failure("missing_code")
        return OAuthCallback.Success(code, parameters["state"])
    }

    private fun parseQuery(query: String): Map<String, String> = query.split("&")
        .mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            decode(key) to decode(value)
        }
        .toMap()

    private fun encode(value: String): String = try {
        URLEncoder.encode(value, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        value
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        value
    }
}
