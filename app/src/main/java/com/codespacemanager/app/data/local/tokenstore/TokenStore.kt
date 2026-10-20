package com.codespacemanager.app.data.local.tokenstore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.codespacemanager.app.platform.crypto.TokenCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tresor fuer OAuth-Tokens.
 *
 * Die Tokens werden mit einem AES/GCM-Schluessel aus dem Android Keystore verschluesselt und
 * in einer EncryptedSharedPreferences abgelegt. Der Zugriff erfolgt synchron (schnell genug
 * fuer den OkHttp-Interceptor, der auf einem Hintergrund-Thread laeuft).
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: TokenCipher,
) {

    private val prefs: SharedPreferences by lazy { createPreferences(context) }

    fun saveToken(login: String, token: String) {
        prefs.edit().putString(key(login), cipher.encrypt(token)).apply()
    }

    fun tokenFor(login: String): String? {
        val encrypted = prefs.getString(key(login), null) ?: return null
        return runCatching { cipher.decrypt(encrypted) }.getOrNull()
    }

    fun removeToken(login: String) {
        prefs.edit().remove(key(login)).apply()
    }

    fun storedLogins(): List<String> = prefs.all.keys
        .filter { it.startsWith(PREFIX) }
        .map { it.removePrefix(PREFIX) }

    /** Verschluesselter Token (fuer die Room-Entity). */
    fun encryptedToken(login: String): String? = prefs.getString(key(login), null)

    private fun key(login: String) = "$PREFIX$login"

    companion object {
        private const val PREFIX = "token_"
        private const val PREFS_NAME = "codespace_manager_secure_tokens"

        fun createPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
