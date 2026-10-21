package com.codespacemanager.app.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codespacemanager.app.data.repository.AccountRepository
import com.codespacemanager.app.domain.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val activeLogin: String? = null,
    val loading: Boolean = true,
)

sealed interface AccountsEvent {
    data class Message(val text: String) : AccountsEvent
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AccountsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AccountsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            accountRepository.accounts.collect { accounts ->
                _state.value = _state.value.copy(
                    accounts = accounts,
                    activeLogin = accountRepository.activeAccountLogin(),
                    loading = false,
                )
            }
        }
    }

    fun switchAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.setActiveAccount(account.login)
            _state.value = _state.value.copy(activeLogin = account.login)
            _events.tryEmit(AccountsEvent.Message("${account.login} ist jetzt aktiv"))
        }
    }

    fun removeAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.removeAccount(account.login)
            _state.value = _state.value.copy(activeLogin = accountRepository.activeAccountLogin())
            _events.tryEmit(AccountsEvent.Message("${account.login} entfernt"))
        }
    }
}
