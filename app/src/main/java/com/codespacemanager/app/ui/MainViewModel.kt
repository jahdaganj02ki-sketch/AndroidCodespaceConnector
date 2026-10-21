package com.codespacemanager.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.repository.AccountRepository
import com.codespacemanager.app.data.repository.AuthCoordinator
import com.codespacemanager.app.domain.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel der Activity: verarbeitet den Redirect (`com.codespacemanager.app://oauth/callback`)
 * und haelt die Liste der verbundenen Konten (Gate der App).
 * Die Activity ist `singleTask`, der ViewModel ueberlebt den Redirect.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authCoordinator: AuthCoordinator,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<Account>?>(null)

    /** Konten der App; `null`, solange die Liste noch nicht geladen ist. */
    val accounts: StateFlow<List<Account>?> = _accounts.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.accounts.collect { loaded ->
                _accounts.value = loaded
            }
        }
    }

    /** Verarbeitet den Redirect. */
    fun handleCallback(uri: String) {
        viewModelScope.launch {
            authCoordinator.handleCallback(uri)
        }
    }
}
