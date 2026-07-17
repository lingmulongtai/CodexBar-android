package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.zenmux.ZenMuxApiService
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

class ZenMuxRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: ZenMuxRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.ZENMUX,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "management-key"
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
            .create(ZenMuxApiService::class.java)
        repository = ZenMuxRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps documented rolling quotas and sends management bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.ZENMUX)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "success": true,
                  "data": {
                    "plan": {"tier": "pro", "expires_at": "2026-08-01T00:00:00Z"},
                    "account_status": "healthy",
                    "quota_5_hour": {
                      "usage_percentage": 0.25,
                      "resets_at": "2026-07-18T00:00:00Z",
                      "max_flows": 100,
                      "used_flows": 25,
                      "remaining_flows": 75
                    },
                    "quota_7_day": {
                      "usage_percentage": 0.5,
                      "resets_at": "2026-07-24T00:00:00Z",
                      "max_flows": 1000,
                      "used_flows": 500,
                      "remaining_flows": 500
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.ZENMUX, quota.service)
        assertEquals("pro", quota.tier)
        assertEquals(listOf("5-Hour", "7-Day"), quota.windows.map { it.label })
        assertEquals(0.25, quota.windows[0].utilization, 0.0001)
        assertEquals(5L * 60L * 60L, quota.windows[0].windowDurationSeconds)
        assertEquals(0.5, quota.windows[1].utilization, 0.0001)
        assertEquals(7L * 24L * 60L * 60L, quota.windows[1].windowDurationSeconds)

        val request = server.takeRequest()
        assertEquals("/subscription/detail", request.path)
        assertEquals("Bearer management-key", request.getHeader("Authorization"))
    }

    @Test
    fun `rejects an inference credential shape before network access`() = runTest {
        val wrongCredential = Credential.ProviderSecretCredential(
            service = AiService.ZENMUX,
            kind = ProviderSecretKind.COOKIE_HEADER,
            accessToken = "not-a-management-key"
        )

        val result = repository.validateCredential(wrongCredential)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.AuthError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `maps rejected management key to terminal authentication error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }
}
