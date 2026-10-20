package com.codespacemanager.app.data.remote.tunnel

import com.codespacemanager.app.domain.gateway.AppSettings
import com.codespacemanager.app.domain.gateway.SettingsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Einfache In-Memory-Implementierung fuer Tests. */
class FakeSettingsGateway(initial: AppSettings = AppSettings()) : SettingsGateway {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
