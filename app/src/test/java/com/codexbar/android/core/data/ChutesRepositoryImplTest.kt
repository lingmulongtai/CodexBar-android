package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.chutes.ChutesApiService
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

class ChutesRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: ChutesRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.CHUTES,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "cpk_test"
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
            .create(ChutesApiService::class.java)
        repository = ChutesRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps four hour and monthly subscription caps`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CHUTES)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "plan_name": "Pro",
                    "rolling_4h": {
                      "limit": 100,
                      "used": 25,
                      "reset_at": "2026-07-18T12:00:00Z"
                    },
                    "monthly_usage": {
                      "limit": 1000,
                      "remaining": 400,
                      "renews_at": "2026-08-01T00:00:00Z"
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.CHUTES, quota.service)
        assertEquals("Pro", quota.tier)
        assertEquals(listOf("4-Hour", "Monthly"), quota.windows.map { it.label })
        assertEquals(0.25, quota.windows[0].utilization, 0.0001)
        assertEquals(4L * 60L * 60L, quota.windows[0].windowDurationSeconds)
        assertEquals(0.6, quota.windows[1].utilization, 0.0001)

        val request = server.takeRequest()
        assertEquals("/users/me/subscription_usage", request.path)
        assertEquals("Bearer cpk_test", request.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `falls back to quota definitions when subscription payload has no usage`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(
            MockResponse().setBody(
                """{"quotas":[{"name":"Search","limit":20,"used":5}]}"""
            )
        )

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Success)
        assertEquals("/users/me/subscription_usage", server.takeRequest().path)
        assertEquals("/users/me/quotas", server.takeRequest().path)
    }

    @Test
    fun `maps rejected key to terminal authentication error without fallback`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
        assertEquals(1, server.requestCount)
    }
}
