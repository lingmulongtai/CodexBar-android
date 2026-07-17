package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.zai.ZaiApiService
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class ZaiRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: ZaiRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.ZAI,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "zai-api-token"
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
            .create(ZaiApiService::class.java)
        repository = ZaiRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps token MCP and rolling windows using upstream precedence`() = runTest {
        `when`(prefsManager.loadCredential(AiService.ZAI)).thenReturn(credential)
        server.enqueue(
            jsonResponse(
                """
                {
                  "code": 200,
                  "success": true,
                  "data": {
                    "plan_type": "GLM Coding Pro",
                    "limits": [
                      {
                        "type": "TOKENS_LIMIT",
                        "unit": 1,
                        "number": 7,
                        "usage": 1000,
                        "currentValue": 150,
                        "remaining": 800,
                        "percentage": 99,
                        "nextResetTime": 1785542400000
                      },
                      {
                        "type": "TIME_LIMIT",
                        "unit": 5,
                        "number": 1,
                        "usage": 100,
                        "currentValue": 5,
                        "remaining": 90,
                        "percentage": 99
                      },
                      {
                        "type": "TOKENS_LIMIT",
                        "unit": 3,
                        "number": 5,
                        "usage": 100,
                        "currentValue": 40,
                        "remaining": 60,
                        "percentage": 99
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.ZAI, quota.service)
        assertEquals("GLM Coding Pro", quota.tier)
        assertEquals(listOf("Tokens · 7-Day", "MCP", "5-Hour"), quota.windows.map { it.label })
        assertEquals(0.20, quota.windows[0].utilization, 0.0001)
        assertEquals(0.10, quota.windows[1].utilization, 0.0001)
        assertEquals(0.40, quota.windows[2].utilization, 0.0001)
        assertEquals(7L * 24L * 60L * 60L, quota.windows[0].windowDurationSeconds)
        assertEquals(60L, quota.windows[1].windowDurationSeconds)
        assertEquals(5L * 60L * 60L, quota.windows[2].windowDurationSeconds)
        assertNotNull(quota.windows[0].resetsAt)

        val request = server.takeRequest()
        assertEquals("/api/monitor/usage/quota/limit", request.path)
        assertEquals("Bearer zai-api-token", request.getHeader("Authorization"))
    }

    @Test
    fun `uses percentage when raw quota counts are unavailable`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "code": 200,
                  "success": true,
                  "data": {
                    "limits": [
                      {"type":"TOKENS_LIMIT","unit":3,"number":5,"percentage":37}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.validateAndFetchForTest(credential)

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(0.37, quota.windows.single().utilization, 0.0001)
        assertEquals("Tokens", quota.windows.single().label)
    }

    @Test
    fun `rejects whitespace in API token before network access`() = runTest {
        val invalid = credential.copy(accessToken = "token with spaces")

        val result = repository.validateCredential(invalid)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.AuthError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `maps application-level credential rejection to terminal auth error`() = runTest {
        server.enqueue(
            jsonResponse("""{"code":401,"success":false,"msg":"unauthorized"}""")
        )

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }

    @Test
    fun `rejects a successful response without supported quota windows`() = runTest {
        server.enqueue(
            jsonResponse("""{"code":200,"success":true,"data":{"limits":[]}}""")
        )

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.ParseError)
    }

    private suspend fun ZaiRepositoryImpl.validateAndFetchForTest(
        credential: Credential.ProviderSecretCredential
    ): Result<com.codexbar.android.core.domain.model.QuotaInfo, AppError> {
        `when`(prefsManager.loadCredential(AiService.ZAI)).thenReturn(credential)
        return fetchQuota()
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
