package com.codespacemanager.app.platform.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class TokenCipherTest {

    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `verschluesselter text laesst sich wieder entschluesseln`() {
        val cipher = TokenCipher { key() }
        val encrypted = cipher.encrypt("ghp_1234567890")
        assertThat(cipher.decrypt(encrypted)).isEqualTo("ghp_1234567890")
    }

    @Test
    fun `verschluesselter text enthaelt den klartext nicht`() {
        val cipher = TokenCipher { key() }
        val encrypted = cipher.encrypt("ghp_1234567890")
        assertThat(encrypted).doesNotContain("ghp_1234567890")
    }

    @Test
    fun `gleiche eingabe ergibt unterschiedliche ausgabe`() {
        val cipher = TokenCipher { key() }
        val first = cipher.encrypt("token")
        val second = cipher.encrypt("token")
        assertThat(first).isNotEqualTo(second)
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second))
    }

    @Test
    fun `falscher schluessel liefert einen fehler`() {
        val encrypted = TokenCipher { key() }.encrypt("token")
        val other = TokenCipher { key() }
        val result = runCatching { other.decrypt(encrypted) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `leerer string funktioniert`() {
        val cipher = TokenCipher { key() }
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty()
    }
}
