package com.codespacemanager.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PkceTest {

    @Test
    fun `challenge entspricht dem Testvektor aus RFC 7636`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertThat(Pkce.codeChallenge(verifier))
            .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    }

    @Test
    fun `challenge enthaelt kein padding und ist url-safe`() {
        val challenge = Pkce.codeChallenge(Pkce.generateCodeVerifier())
        assertThat(challenge).doesNotContain("=")
        assertThat(challenge).doesNotContain("+")
        assertThat(challenge).doesNotContain("/")
    }

    @Test
    fun `code verifier hat mindestens 43 Zeichen`() {
        assertThat(Pkce.generateCodeVerifier().length).isAtLeast(43)
    }

    @Test
    fun `state ist zufaellig und eindeutig`() {
        val first = Pkce.generateState()
        val second = Pkce.generateState()
        assertThat(first).isNotEqualTo(second)
        assertThat(first.length).isAtLeast(16)
    }
}
