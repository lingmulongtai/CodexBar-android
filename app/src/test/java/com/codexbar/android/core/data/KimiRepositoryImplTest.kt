package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.kimi.KimiApiService
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

class KimiRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: KimiRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.KIMI,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "kimi-code-key"
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
            .create(KimiApiService::class.java)
        repository = KimiRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps string and numeric quotas and sends bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.KIMI)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "usage": {
                    "limit": "2048",
                    "used": 214,
                    "remaining": "1834",
                    "resetTime": "2026-07-24T15:23:13.716839300Z"
                  },
                  "limits": [{
                    "window": {"duration": "300", "timeUnit": "TIME_UNIT_MINUTE"},
                    "detail": {
                      "limit": 200,
                      "remaining": 61,
                      "reset_time": "2026-07-18T13:33:02.717479433Z"
                    }
                  }]
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.KIMI, quota.service)
        assertEquals(listOf("5-Hour", "7-Day"), quota.windows.map { it.label })
        assertEquals(139.0 / 200.0, quota.windows[0].utilization, 0.0001)
        assertEquals(5L * 60L * 60L, quota.windows[0].windowDurationSeconds)
        assertEquals(214.0 / 2048.0, quota.windows[1].utilization, 0.0001)
        assertEquals(7L * 24L * 60L * 60L, quota.windows[1].windowDurationSeconds)

        val request = server.takeRequest()
        assertEquals("/coding/v1/usages", request.path)
        assertEquals("Bearer kimi-code-key", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `rejects malformed key before network access`() = runTest {
        val malformed = credential.copy(accessToken = "line one\nline two")

        val result = repository.validateCredential(malformed)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.AuthError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `maps rejected key to terminal authentication error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }
}
