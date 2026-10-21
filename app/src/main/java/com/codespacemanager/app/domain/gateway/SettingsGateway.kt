package com.codespacemanager.app.domain.gateway

import com.codespacemanager.app.domain.model.Geo
import com.codespacemanager.app.domain.usecase.DevContainerTemplates
import kotlinx.coroutines.flow.Flow

/** Persistente Einstellungen der App (DataStore). */
data class AppSettings(
    val defaultGeo: Geo = Geo.EUROPE_WEST,
    val defaultMachine: String = "basicLinux32gb",
    val idleTimeoutMinutes: Int = 30,
    val clientIdOverride: String = "",
    val clientSecretOverride: String = "",
    val autoPrivatePortOnExit: Boolean = true,
    val idlePrivatePortMinutes: Int = 15,
    val tunnelApiPathVariant: String = "",
    val extensionIds: List<String> = DevContainerTemplates.DEFAULT_EXTENSIONS,
    val hasSeenAgentKeyHint: Boolean = false,
    val activeAccountLogin: String? = null,
    /** State des laufenden OAuth-Flows (leer = kein Flow aktiv). */
    val pendingOAuthState: String = "",
    /** PKCE-Verifier des laufenden OAuth-Flows. */
    val pendingOAuthVerifier: String = "",
)

interface SettingsGateway {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
