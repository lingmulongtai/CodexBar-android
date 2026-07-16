package com.codexbar.android.di

import com.codexbar.android.core.data.ClaudeRepositoryImpl
import com.codexbar.android.core.data.CodexRepositoryImpl
import com.codexbar.android.core.data.CopilotRepositoryImpl
import com.codexbar.android.core.data.CursorRepositoryImpl
import com.codexbar.android.core.data.GeminiRepositoryImpl
import com.codexbar.android.core.data.KimiRepositoryImpl
import com.codexbar.android.core.data.ZaiRepositoryImpl
import com.codexbar.android.core.data.ZenMuxRepositoryImpl
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.claude.ClaudeApiService
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.copilot.CopilotApiService
import com.codexbar.android.core.network.cursor.CursorApiService
import com.codexbar.android.core.network.gemini.GeminiCompanionClient
import com.codexbar.android.core.network.kimi.KimiApiService
import com.codexbar.android.core.network.zai.ZaiApiService
import com.codexbar.android.core.network.zenmux.ZenMuxApiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.TokenRefreshCoordinator
import dagger.Module
import dagger.MapKey
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@MapKey
annotation class AiServiceKey(val value: AiService)

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.CLAUDE)
    fun provideClaudeRepository(
        apiService: ClaudeApiService,
        tokenRefreshService: ClaudeTokenRefreshService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = ClaudeRepositoryImpl(apiService, tokenRefreshService, prefsManager)

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.CODEX)
    fun provideCodexRepository(
        apiService: CodexApiService,
        tokenRefreshService: CodexTokenRefreshService,
        prefsManager: EncryptedPrefsManager,
        tokenRefreshCoordinator: TokenRefreshCoordinator
    ): QuotaRepository = CodexRepositoryImpl(apiService, tokenRefreshService, prefsManager, tokenRefreshCoordinator)

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.GEMINI)
    fun provideGeminiRepository(
        companionClient: GeminiCompanionClient,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = GeminiRepositoryImpl(companionClient, prefsManager)

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.COPILOT)
    fun provideCopilotRepository(
        apiService: CopilotApiService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = CopilotRepositoryImpl(apiService, prefsManager)

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.CURSOR)
    fun provideCursorRepository(
        apiService: CursorApiService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = CursorRepositoryImpl(apiService, prefsManager)

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.ZAI)
    fun provideZaiRepository(
        apiService: ZaiApiService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = ZaiRepositoryImpl(apiService, prefsManager)

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.ZENMUX)
    fun provideZenMuxRepository(
        apiService: ZenMuxApiService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = ZenMuxRepositoryImpl(apiService, prefsManager)

    @Provides
    @Singleton
    @IntoMap
    @AiServiceKey(AiService.KIMI)
    fun provideKimiRepository(
        apiService: KimiApiService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = KimiRepositoryImpl(apiService, prefsManager)
}
