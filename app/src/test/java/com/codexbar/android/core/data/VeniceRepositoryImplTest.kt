package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.venice.VeniceApiService
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

class VeniceRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: VeniceRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.VENICE,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "venice-test-key"
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
            .create(VeniceApiService::class.java)
        repository = VeniceRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps documented DIEM allocation and sends bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.VENICE)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "canConsume": true,
                  "consumptionCurrency": "DIEM",
                  "balances": {
                    "diem": 90.5,
                    "usd": 25.0
                  },
                  "diemEpochAllocation": 100.0
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.VENICE, quota.service)
        assertEquals("DIEM epoch", quota.windows.single().label)
        assertEquals(0.095, quota.windows.single().utilization, 0.0001)
        assertEquals(86_400L, quota.windows.single().windowDurationSeconds)
        assertEquals(100.0, quota.extraUsage?.monthlyLimit ?: -1.0, 0.0001)
        assertEquals(9.5, quota.extraUsage?.usedCredits ?: -1.0, 0.0001)
        assertEquals(
            "API enabled · Uses DIEM · DIEM 90.50 · USD 25.00",
            quota.tier
        )

        val request = server.takeRequest()
        assertEquals("/billing/balance", request.path)
        assertEquals("Bearer venice-test-key", request.getHeader("Authorization"))
    }

    @Test
    fun `keeps balance only accounts connected without inventing a bar`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "canConsume": false,
                  "consumptionCurrency": "USD",
                  "balances": { "usd": 4.25 },
                  "diemEpochAllocation": 0.0
                }
                """.trimIndent()
            )
        )

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Success)
    }

    @Test
    fun `rejects malformed key before making a request`() = runTest {
        val malformed = credential.copy(accessToken = "venice key")

        val result = repository.validateCredential(malformed)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
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
