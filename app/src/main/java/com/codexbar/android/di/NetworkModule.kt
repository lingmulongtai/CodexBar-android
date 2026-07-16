package com.codexbar.android.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.codexbar.android.BuildConfig
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.network.RetryInterceptor
import com.codexbar.android.core.network.ResponseSizeLimitInterceptor
import com.codexbar.android.core.network.claude.ClaudeApiService
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.copilot.CopilotApiService
import com.codexbar.android.core.network.oauth.CodexDeviceAuthService
import com.codexbar.android.core.network.oauth.GitHubDeviceAuthService
import com.codexbar.android.core.network.zenmux.ZenMuxApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeTokenClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexTokenClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CopilotClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexDeviceAuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubDeviceAuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ZenMuxClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun baseOkHttpBuilder(includeDebugLogging: Boolean): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor())
            .addInterceptor(ResponseSizeLimitInterceptor())

        if (BuildConfig.IS_DEBUG && includeDebugLogging) {
            builder.addInterceptor(createMetadataLoggingInterceptor())
        }

        return builder
    }

    private fun credentialOkHttpBuilder(): OkHttpClient.Builder {
        return baseOkHttpBuilder(includeDebugLogging = false)
            .followRedirects(false)
            .followSslRedirects(false)
    }

    internal fun createMetadataLoggingInterceptor(
        logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
    ): HttpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        redactHeader("X-Api-Key")
        level = HttpLoggingInterceptor.Level.BASIC
    }

    // --- Claude ---

    @Provides
    @Singleton
    @ClaudeClient
    fun provideClaudeOkHttpClient(): OkHttpClient = baseOkHttpBuilder(includeDebugLogging = true).build()

    @Provides
    @Singleton
    fun provideClaudeApiService(
        @ClaudeClient client: OkHttpClient,
        json: Json
    ): ClaudeApiService {
        return Retrofit.Builder()
            .baseUrl(AiService.CLAUDE.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClaudeApiService::class.java)
    }

    @Provides
    @Singleton
    @ClaudeTokenClient
    fun provideClaudeTokenOkHttpClient(): OkHttpClient = credentialOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideClaudeTokenRefreshService(
        @ClaudeTokenClient client: OkHttpClient,
        json: Json
    ): ClaudeTokenRefreshService {
        return Retrofit.Builder()
            .baseUrl(ClaudeTokenRefreshService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClaudeTokenRefreshService::class.java)
    }

    // --- Codex ---

    @Provides
    @Singleton
    @CodexClient
    fun provideCodexOkHttpClient(): OkHttpClient = baseOkHttpBuilder(includeDebugLogging = true).build()

    @Provides
    @Singleton
    fun provideCodexApiService(
        @CodexClient client: OkHttpClient,
        json: Json
    ): CodexApiService {
        return Retrofit.Builder()
            .baseUrl(AiService.CODEX.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CodexApiService::class.java)
    }

    @Provides
    @Singleton
    @CodexTokenClient
    fun provideCodexTokenOkHttpClient(): OkHttpClient = credentialOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideCodexTokenRefreshService(
        @CodexTokenClient client: OkHttpClient,
        json: Json
    ): CodexTokenRefreshService {
        return Retrofit.Builder()
            .baseUrl(CodexTokenRefreshService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CodexTokenRefreshService::class.java)
    }

    @Provides
    @Singleton
    @CodexDeviceAuthClient
    fun provideCodexDeviceAuthOkHttpClient(): OkHttpClient = credentialOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideCodexDeviceAuthService(
        @CodexDeviceAuthClient client: OkHttpClient,
        json: Json
    ): CodexDeviceAuthService {
        return Retrofit.Builder()
            .baseUrl(CodexDeviceAuthService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CodexDeviceAuthService::class.java)
    }

    // --- GitHub Copilot ---

    @Provides
    @Singleton
    @CopilotClient
    fun provideCopilotOkHttpClient(): OkHttpClient = baseOkHttpBuilder(includeDebugLogging = true).build()

    @Provides
    @Singleton
    fun provideCopilotApiService(
        @CopilotClient client: OkHttpClient,
        json: Json
    ): CopilotApiService {
        return Retrofit.Builder()
            .baseUrl(AiService.COPILOT.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CopilotApiService::class.java)
    }

    @Provides
    @Singleton
    @GitHubDeviceAuthClient
    fun provideGitHubDeviceAuthOkHttpClient(): OkHttpClient = credentialOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideGitHubDeviceAuthService(
        @GitHubDeviceAuthClient client: OkHttpClient,
        json: Json
    ): GitHubDeviceAuthService {
        return Retrofit.Builder()
            .baseUrl(GitHubDeviceAuthService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubDeviceAuthService::class.java)
    }

    // --- ZenMux ---

    @Provides
    @Singleton
    @ZenMuxClient
    fun provideZenMuxOkHttpClient(): OkHttpClient = credentialOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideZenMuxApiService(
        @ZenMuxClient client: OkHttpClient,
        json: Json
    ): ZenMuxApiService {
        return Retrofit.Builder()
            .baseUrl(AiService.ZENMUX.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ZenMuxApiService::class.java)
    }
}
