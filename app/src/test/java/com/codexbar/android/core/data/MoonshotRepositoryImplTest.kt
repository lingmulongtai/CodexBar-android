package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.moonshot.MoonshotApiService
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

class MoonshotRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: MoonshotRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.MOONSHOT,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "sk-moonshot-test"
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
            .create(MoonshotApiService::class.java)
        repository = MoonshotRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps documented balance and sends bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.MOONSHOT)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "code": 0,
                  "data": {
                    "available_balance": 49.58894,
                    "voucher_balance": 46.58893,
                    "cash_balance": 3.00001
                  },
                  "scode": "0x0",
                  "status": true
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.MOONSHOT, quota.service)
        assertTrue(quota.windows.isEmpty())
        assertEquals(null, quota.extraUsage)
        assertEquals(
            "Available balance 49.59 (cash 3.00 + voucher 46.59)",
            quota.tier
        )

        val request = server.takeRequest()
        assertEquals("/v1/users/me/balance", request.path)
        assertEquals("Bearer sk-moonshot-test", request.getHeader("Authorization"))
    }

    @Test
    fun `surfaces negative cash as a deficit`() = runTest {
        `when`(prefsManager.loadCredential(AiService.MOONSHOT)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "available_balance": 0.0,
                    "voucher_balance": 2.0,
                    "cash_balance": -2.0
                  },
                  "scode": "0x0",
                  "status": true
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        assertEquals(
            "Available balance 0.00 (cash deficit -2.00 + voucher 2.00)",
            (result as Result.Success).value.tier
        )
    }

    @Test
    fun `rejects malformed key before making a request`() = runTest {
        val malformed = credential.copy(accessToken = "moonshot key")

        val result = repository.validateCredential(malformed)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `maps unauthorized key to terminal authentication error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }
}
