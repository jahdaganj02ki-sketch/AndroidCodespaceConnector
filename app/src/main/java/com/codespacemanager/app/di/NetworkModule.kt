package com.codespacemanager.app.di

import com.codespacemanager.app.core.ApiException
import com.codespacemanager.app.data.local.tokenstore.TokenStore
import com.codespacemanager.app.data.remote.api.AuthApi
import com.codespacemanager.app.data.remote.api.CodespaceApi
import com.codespacemanager.app.data.remote.api.OrgRepoApi
import com.codespacemanager.app.data.remote.api.RepoApi
import com.codespacemanager.app.data.remote.api.UserApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubAuthApi

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TunnelClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val API_BASE_URL = "https://api.github.com/"
    private const val AUTH_BASE_URL = "https://github.com/"
    private const val GITHUB_API_VERSION = "2026-03-10"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideJson(): Json = json

    @Provides
    @Singleton
    fun provideTokenProvider(
        tokenStore: TokenStore,
        settingsStore: com.codespacemanager.app.data.local.datastore.SettingsStore,
    ): TokenProvider = object : TokenProvider {
        override fun currentToken(): String? {
            val login = runBlocking { settingsStore.current().activeAccountLogin } ?: return null
            return tokenStore.tokenFor(login)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenProvider: TokenProvider): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val isApiHost = request.url.host.endsWith("api.github.com")
            val token = if (isApiHost) tokenProvider.currentToken() else null
            val newRequest = if (token.isNullOrBlank()) {
                request
            } else {
                request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
            chain.proceed(
                newRequest.newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                    .build(),
            )
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @GithubAuthApi
    fun provideAuthRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(AUTH_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(@GithubAuthApi retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideRepoApi(retrofit: Retrofit): RepoApi = retrofit.create(RepoApi::class.java)

    @Provides
    @Singleton
    fun provideOrgRepoApi(retrofit: Retrofit): OrgRepoApi = retrofit.create(OrgRepoApi::class.java)

    @Provides
    @Singleton
    fun provideCodespaceApi(retrofit: Retrofit): CodespaceApi = retrofit.create(CodespaceApi::class.java)
}

/** Liefert das Token des aktiven Kontos fuer den HTTP-Interceptor. */
interface TokenProvider {
    fun currentToken(): String?
}

/** Wandelt Fehler der GitHub-API in [ApiException] um. */
fun Throwable.toApiException(): ApiException = when (this) {
    is ApiException -> this
    else -> ApiException(0, message ?: "Unbekannter Fehler")
}
