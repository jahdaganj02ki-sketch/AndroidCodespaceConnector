package com.codespacemanager.app.domain.usecase

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE-Helfer (RFC 7636) fuer den GitHub-OAuth-Flow.
 * Challenge = BASE64URL(SHA-256(Verifier)) ohne Padding.
 */
object Pkce {

    private const val VERIFIER_BYTES = 32

    fun generateCodeVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(VERIFIER_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun codeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun generateState(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
