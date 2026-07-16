package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
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

class DeepSeekRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: DeepSeekRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.DEEPSEEK,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "sk-deepseek-test"
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
            .create(DeepSeekApiService::class.java)
        repository = DeepSeekRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps documented balances and sends bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.DEEPSEEK)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "is_available": true,
                  "balance_infos": [
                    {
                      "currency": "USD",
                      "total_balance": "110.00",
                      "granted_balance": "10.00",
                      "topped_up_balance": "100.00"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.DEEPSEEK, quota.service)
        assertTrue(quota.windows.isEmpty())
        assertEquals(null, quota.extraUsage)
        assertEquals("Available · USD 110.00 (paid 100.00 + grant 10.00)", quota.tier)

        val request = server.takeRequest()
        assertEquals("/user/balance", request.path)
        assertEquals("Bearer sk-deepseek-test", request.getHeader("Authorization"))
    }

    @Test
    fun `rejects malformed key before making a request`() = runTest {
        val malformed = credential.copy(accessToken = "sk bad")

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
