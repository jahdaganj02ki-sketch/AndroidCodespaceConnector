package com.codespacemanager.app.platform.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM-Verschluesselung von Tokens.
 *
 * Bewusst ohne Android-Abhaengigkeiten, damit die Logik als Unit-Test lauffaehig bleibt.
 * Der Schluessel kommt aus dem Android Keystore (siehe [KeystoreKeyProvider]).
 */
class TokenCipher(private val keyProvider: () -> SecretKey) {

    private val random = SecureRandom()

    fun encrypt(plainText: String): String {
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(nonce.size + cipherText.size)
        System.arraycopy(nonce, 0, combined, 0, nonce.size)
        System.arraycopy(cipherText, 0, combined, nonce.size, cipherText.size)
        return Base64.encode(combined)
    }

    fun decrypt(payload: String): String {
        val combined = Base64.decode(payload)
        require(combined.size > NONCE_SIZE) { "Verschluesselter Wert ist ungueltig" }
        val nonce = combined.copyOfRange(0, NONCE_SIZE)
        val cipherText = combined.copyOfRange(NONCE_SIZE, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val NONCE_SIZE = 12
    }
}

/** Minimale Base64-Hilfsfunktion. */
internal object Base64 {
    fun encode(data: ByteArray): String = java.util.Base64.getEncoder().encodeToString(data)

    fun decode(data: String): ByteArray = java.util.Base64.getDecoder().decode(data)
}
