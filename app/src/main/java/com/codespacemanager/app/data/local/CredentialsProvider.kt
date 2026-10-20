package com.codespacemanager.app.data.local

import com.codespacemanager.app.BuildConfig
import com.codespacemanager.app.data.local.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert die OAuth-Zugangsdaten: Einstellung (Override) hat Vorrang vor dem
 * per CI injizierten BuildConfig-Wert.
 */
@Singleton
class CredentialsProvider @Inject constructor(
    private val settingsStore: SettingsStore,
) {

    suspend fun clientId(): String =
        settingsStore.current().clientIdOverride.ifBlank { BuildConfig.GITHUB_CLIENT_ID }.trim()

    suspend fun clientSecret(): String =
        settingsStore.current().clientSecretOverride.ifBlank { BuildConfig.GITHUB_CLIENT_SECRET }.trim()

    suspend fun hasCredentials(): Boolean = clientId().isNotBlank() && clientSecret().isNotBlank()
}
