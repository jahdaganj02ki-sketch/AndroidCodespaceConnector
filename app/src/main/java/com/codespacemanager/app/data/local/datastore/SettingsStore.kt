package com.codespacemanager.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codespacemanager.app.domain.gateway.AppSettings
import com.codespacemanager.app.domain.gateway.SettingsGateway
import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.usecase.DevContainerTemplates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "codespace_manager_settings",
)

/** Persistente Einstellungen (DataStore/Preferences). */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsGateway {

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    override val settings: Flow<AppSettings> = store.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { toSettings(it) }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            val updated = transform(toSettings(preferences))
            applySettings(preferences, updated)
        }
    }

    private fun toSettings(preferences: Preferences): AppSettings {
        val extensionIds = preferences[KEY_EXTENSIONS]
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: DevContainerTemplates.DEFAULT_EXTENSIONS
        return AppSettings(
            defaultGeo = Geo.from(preferences[KEY_GEO]),
            defaultMachine = preferences[KEY_MACHINE] ?: DEFAULT_MACHINE,
            idleTimeoutMinutes = preferences[KEY_IDLE_TIMEOUT] ?: DEFAULT_IDLE_TIMEOUT,
            clientIdOverride = preferences[KEY_CLIENT_ID].orEmpty(),
            clientSecretOverride = preferences[KEY_CLIENT_SECRET].orEmpty(),
            autoPrivatePortOnExit = preferences[KEY_AUTO_PRIVATE] ?: true,
            idlePrivatePortMinutes = preferences[KEY_IDLE_PRIVATE_MINUTES] ?: DEFAULT_IDLE_PRIVATE_MINUTES,
            tunnelApiPathVariant = preferences[KEY_TUNNEL_VARIANT].orEmpty(),
            extensionIds = extensionIds,
            hasSeenAgentKeyHint = preferences[KEY_SEEN_AGENT_HINT] ?: false,
            setupCompleted = preferences[KEY_SETUP_COMPLETED] ?: false,
            activeAccountLogin = preferences[KEY_ACTIVE_ACCOUNT],
        )
    }

    private fun applySettings(preferences: androidx.datastore.preferences.core.MutablePreferences, settings: AppSettings) {
        preferences[KEY_GEO] = settings.defaultGeo.apiValue
        preferences[KEY_MACHINE] = settings.defaultMachine
        preferences[KEY_IDLE_TIMEOUT] = settings.idleTimeoutMinutes
        preferences[KEY_CLIENT_ID] = settings.clientIdOverride
        preferences[KEY_CLIENT_SECRET] = settings.clientSecretOverride
        preferences[KEY_AUTO_PRIVATE] = settings.autoPrivatePortOnExit
        preferences[KEY_IDLE_PRIVATE_MINUTES] = settings.idlePrivatePortMinutes
        preferences[KEY_TUNNEL_VARIANT] = settings.tunnelApiPathVariant
        preferences[KEY_EXTENSIONS] = settings.extensionIds.toSet()
        preferences[KEY_SEEN_AGENT_HINT] = settings.hasSeenAgentKeyHint
        preferences[KEY_SETUP_COMPLETED] = settings.setupCompleted
        val active = settings.activeAccountLogin
        if (active == null) preferences.remove(KEY_ACTIVE_ACCOUNT) else preferences[KEY_ACTIVE_ACCOUNT] = active
    }

    companion object {
        const val DEFAULT_MACHINE = "basicLinux32gb"
        const val DEFAULT_IDLE_TIMEOUT = 30
        const val DEFAULT_IDLE_PRIVATE_MINUTES = 15

        private val KEY_GEO = stringPreferencesKey("default_geo")
        private val KEY_MACHINE = stringPreferencesKey("default_machine")
        private val KEY_IDLE_TIMEOUT = intPreferencesKey("idle_timeout_minutes")
        private val KEY_CLIENT_ID = stringPreferencesKey("client_id")
        private val KEY_CLIENT_SECRET = stringPreferencesKey("client_secret")
        private val KEY_AUTO_PRIVATE = booleanPreferencesKey("auto_private_port")
        private val KEY_IDLE_PRIVATE_MINUTES = intPreferencesKey("idle_private_minutes")
        private val KEY_TUNNEL_VARIANT = stringPreferencesKey("tunnel_api_path_variant")
        private val KEY_EXTENSIONS = stringSetPreferencesKey("extension_ids")
        private val KEY_SEEN_AGENT_HINT = booleanPreferencesKey("seen_agent_key_hint")
        private val KEY_SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        private val KEY_ACTIVE_ACCOUNT = stringPreferencesKey("active_account")
    }
}
