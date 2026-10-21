package com.codespacemanager.app.platform.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OAuthTest {

    private val request = OAuthRequest(state = "state-1", codeVerifier = "verifier-1")

    @Test
    fun `GitHub-Variante setzt prompt select_account`() {
        val url = OAuth.authorizeUrl("client-1", request, SignInProvider.GITHUB)

        assertThat(url).contains("prompt=select_account")
    }

    @Test
    fun `Google-Variante setzt kein prompt`() {
        val url = OAuth.authorizeUrl("client-1", request, SignInProvider.GOOGLE)

        assertThat(url).doesNotContain("prompt")
    }

    @Test
    fun `beide Varianten enthalten state scope und PKCE-Parameter`() {
        SignInProvider.entries.forEach { provider ->
            val url = OAuth.authorizeUrl("client-1", request, provider)

            assertThat(url).contains("client_id=client-1")
            assertThat(url).contains("state=state-1")
            assertThat(url).contains("scope=repo+codespace+read%3Aorg")
            assertThat(url).contains("code_challenge=")
            assertThat(url).contains("code_challenge_method=S256")
            assertThat(url).contains("redirect_uri=com.codespacemanager.app%3A%2F%2Foauth%2Fcallback")
        }
    }

    @Test
    fun `login-Parameter wird nur bei gesetztem Login angehaengt`() {
        val ohneLogin = OAuth.authorizeUrl("client-1", request)
        val mitLogin = OAuth.authorizeUrl("client-1", request, login = "octocat")

        assertThat(ohneLogin).doesNotContain("login=")
        assertThat(mitLogin).contains("login=octocat")
    }
}
