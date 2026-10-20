package com.codespacemanager.app.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.local.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val clientId: String = "",
    val clientSecret: String = "",
    val saved: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsStore.current()
            _state.value = _state.value.copy(
                clientId = settings.clientIdOverride,
                clientSecret = settings.clientSecretOverride,
            )
        }
    }

    fun setClientId(value: String) {
        _state.value = _state.value.copy(clientId = value)
    }

    fun setClientSecret(value: String) {
        _state.value = _state.value.copy(clientSecret = value)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            settingsStore.update {
                it.copy(
                    clientIdOverride = current.clientId.trim(),
                    clientSecretOverride = current.clientSecret.trim(),
                    setupCompleted = true,
                )
            }
            _state.value = current.copy(saved = true)
            onSaved()
        }
    }
}
