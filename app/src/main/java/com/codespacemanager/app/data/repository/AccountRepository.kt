package com.codespacemanager.app.data.repository

import com.codespacemanager.app.data.local.db.AccountDao
import com.codespacemanager.app.data.local.db.AccountEntity
import com.codespacemanager.app.data.local.tokenstore.TokenStore
import com.codespacemanager.app.domain.gateway.AuthGateway
import com.codespacemanager.app.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Kontenverwaltung: Token-Tausch, Speicherung, Umschalten, Entfernen. */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val tokenStore: TokenStore,
    private val settingsStore: com.codespacemanager.app.data.local.datastore.SettingsStore,
    private val authGateway: AuthGateway,
) {

    val accounts: Flow<List<Account>> = accountDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun account(login: String): Account? = accountDao.findById(login.lowercase())?.toDomain()

    suspend fun addAccount(
        clientId: String,
        clientSecret: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): Account {
        val token = authGateway.exchangeCodeForToken(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
        )
        val user = authGateway.currentUser(token)
        val stored = Account(
            id = user.id,
            login = user.login,
            avatarUrl = user.avatarUrl,
            scopes = user.scopes,
            createdAt = System.currentTimeMillis(),
        )
        tokenStore.saveToken(stored.login, token)
        accountDao.upsert(
            AccountEntity(
                id = stored.id,
                login = stored.login,
                avatarUrl = stored.avatarUrl,
                accessTokenEnc = tokenStore.encryptedToken(stored.login),
                scopes = stored.scopes.joinToString(","),
                createdAt = stored.createdAt,
            ),
        )
        setActiveAccount(stored.login)
        return stored
    }

    suspend fun setActiveAccount(login: String) {
        settingsStore.update { it.copy(activeAccountLogin = login) }
    }

    suspend fun activeAccountLogin(): String? = settingsStore.current().activeAccountLogin

    suspend fun removeAccount(login: String) {
        tokenStore.removeToken(login)
        accountDao.delete(login.lowercase())
        val active = settingsStore.current().activeAccountLogin
        if (active.equals(login, ignoreCase = true)) {
            settingsStore.update { it.copy(activeAccountLogin = null) }
        }
    }

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        login = login,
        avatarUrl = avatarUrl,
        scopes = scopes.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        createdAt = createdAt,
    )
}
