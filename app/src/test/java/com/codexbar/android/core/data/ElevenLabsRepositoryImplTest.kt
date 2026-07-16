package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.elevenlabs.ElevenLabsApiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class ElevenLabsRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: ElevenLabsRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.ELEVENLABS,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "elevenlabs-key"
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefsManager = mock(EncryptedPrefsManager::class.java)
        val apiService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(ElevenLabsApiService::class.java)
        repository = ElevenLabsRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps character and voice quotas and sends xi api key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.ELEVENLABS)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "tier": "creator",
                  "status": "active",
                  "character_count": 25000,
                  "character_limit": 100000,
                  "voice_slots_used": 2,
                  "voice_limit": 5,
                  "professional_voice_slots_used": 1,
                  "professional_voice_limit": 3,
                  "next_character_count_reset_unix": 1784400000
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.ELEVENLABS, quota.service)
        assertEquals("creator", quota.tier)
        assertEquals(
            listOf("Characters", "Voice slots", "Professional voices"),
            quota.windows.map { it.label }
        )
        assertEquals(0.25, quota.windows[0].utilization, 0.0001)
        assertEquals(0.4, quota.windows[1].utilization, 0.0001)
        assertEquals(1.0 / 3.0, quota.windows[2].utilization, 0.0001)
        assertEquals(1784400000L, quota.windows[0].resetsAt?.epochSecond)

        val request = server.takeRequest()
        assertEquals("/v1/user/subscription", request.path)
        assertEquals("elevenlabs-key", request.getHeader("xi-api-key"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `rejects credential for another provider before network access`() = runTest {
        val wrongProvider = credential.copy(service = AiService.KIMI)

        val result = repository.validateCredential(wrongProvider)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.AuthError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `maps forbidden key to terminal authentication error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }
}
