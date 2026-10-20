package com.codespacemanager.app.di

import com.codespacemanager.app.platform.crypto.KeystoreKeyProvider
import com.codespacemanager.app.platform.crypto.TokenCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideKeystoreKeyProvider(): KeystoreKeyProvider = KeystoreKeyProvider()

    @Provides
    @Singleton
    fun provideTokenCipher(keyProvider: KeystoreKeyProvider): TokenCipher =
        TokenCipher { keyProvider.getOrCreateKey() }
}
