package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.openrouter.OpenRouterApiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class OpenRouterRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: OpenRouterRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.OPENROUTER,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "sk-or-v1-test"
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
            .create(OpenRouterApiService::class.java)
        repository = OpenRouterRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps limited key from remaining credits and sends bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.OPENROUTER)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "limit": 100.0,
                    "limit_remaining": 74.5,
                    "limit_reset": "monthly",
                    "usage": 90.0,
                    "usage_daily": 1.25,
                    "usage_weekly": 8.5,
                    "usage_monthly": 25.5,
                    "is_free_tier": false,
                    "expires_at": "2027-12-31T23:59:59Z"
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.OPENROUTER, quota.service)
        assertEquals(listOf("Monthly budget"), quota.windows.map { it.label })
        assertEquals(0.255, quota.windows.single().utilization, 0.0001)
        assertNotNull(quota.windows.single().resetsAt)
        assertEquals(100.0, quota.extraUsage?.monthlyLimit ?: 0.0, 0.0001)
        assertEquals(25.5, quota.extraUsage?.usedCredits ?: 0.0, 0.0001)

        val request = server.takeRequest()
        assertEquals("/key", request.path)
        assertEquals("Bearer sk-or-v1-test", request.getHeader("Authorization"))
    }

    @Test
    fun `keeps an unlimited key connected without inventing a quota bar`() = runTest {
        `when`(prefsManager.loadCredential(AiService.OPENROUTER)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "limit": null,
                    "limit_remaining": null,
                    "limit_reset": null,
                    "usage": 12.34,
                    "usage_daily": 0.25,
                    "usage_weekly": 2.0,
                    "usage_monthly": 6.5,
                    "is_free_tier": true
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertTrue(quota.windows.isEmpty())
        assertNull(quota.extraUsage)
        assertTrue(quota.tier.orEmpty().contains("No spending cap"))
        assertTrue(quota.tier.orEmpty().contains("USD 12.34 used"))
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
