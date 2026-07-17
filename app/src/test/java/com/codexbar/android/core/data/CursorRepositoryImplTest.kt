package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.cursor.CursorApiService
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

class CursorRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: CursorRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.CURSOR,
        kind = ProviderSecretKind.COOKIE_HEADER,
        accessToken = "Cookie: WorkosCursorSessionToken=session-token; theme=dark"
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
            .create(CursorApiService::class.java)
        repository = CursorRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps modern billing windows and sends normalized cookie only to Cursor API`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CURSOR)).thenReturn(credential)
        server.enqueue(
            jsonResponse(
                """
                {
                  "billingCycleStart": "2026-07-01T00:00:00Z",
                  "billingCycleEnd": "2026-08-01T00:00:00Z",
                  "membershipType": "pro",
                  "individualUsage": {
                    "plan": {
                      "used": 2500,
                      "limit": 10000,
                      "autoPercentUsed": 20,
                      "apiPercentUsed": 40,
                      "totalPercentUsed": 30
                    },
                    "onDemand": {"enabled": true, "used": 500, "limit": 2000}
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(jsonResponse("""{"email":"person@example.com","name":"Person"}"""))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.CURSOR, quota.service)
        assertEquals("Cursor Pro", quota.tier)
        assertEquals(listOf("Total", "Auto", "API"), quota.windows.map { it.label })
        assertEquals(0.30, quota.windows[0].utilization, 0.0001)
        assertEquals(0.20, quota.windows[1].utilization, 0.0001)
        assertEquals(0.40, quota.windows[2].utilization, 0.0001)
        assertEquals(31L * 24L * 60L * 60L, quota.windows[0].windowDurationSeconds)
        assertNotNull(quota.windows[0].resetsAt)
        assertEquals(20.0, quota.extraUsage?.monthlyLimit ?: -1.0, 0.0001)
        assertEquals(5.0, quota.extraUsage?.usedCredits ?: -1.0, 0.0001)
        assertEquals(0.25, quota.extraUsage?.utilization ?: -1.0, 0.0001)

        repeat(2) {
            val request = server.takeRequest()
            assertEquals(
                "WorkosCursorSessionToken=session-token; theme=dark",
                request.getHeader("Cookie")
            )
        }
    }

    @Test
    fun `legacy request quota replaces modern lanes when available`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "billingCycleStart": "2026-07-01T00:00:00Z",
                  "billingCycleEnd": "2026-08-01T00:00:00Z",
                  "membershipType": "hobby",
                  "individualUsage": {
                    "plan": {
                      "autoPercentUsed": 10,
                      "apiPercentUsed": 20,
                      "totalPercentUsed": 15
                    }
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(jsonResponse("""{"sub":"user-123"}"""))
        server.enqueue(
            jsonResponse(
                """
                {"gpt-4":{"numRequestsTotal":40,"maxRequestUsage":100}}
                """.trimIndent()
            )
        )

        val result = repository.validateAndFetchForTest(credential)

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(listOf("Total"), quota.windows.map { it.label })
        assertEquals(0.40, quota.windows.single().utilization, 0.0001)
        assertEquals("/api/usage?user=user-123", server.takeRequestAt(2).path)
    }

    @Test
    fun `enterprise shared pool is used when an individual plan is absent`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "membershipType": "enterprise",
                  "individualUsage": {},
                  "teamUsage": {
                    "pooled": {"enabled":true,"used":7500,"limit":10000},
                    "onDemand": {"enabled":true,"used":200,"limit":1000}
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(jsonResponse("{}"))

        val result = repository.validateAndFetchForTest(credential)

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(0.75, quota.windows.single().utilization, 0.0001)
        assertEquals(10.0, quota.extraUsage?.monthlyLimit ?: -1.0, 0.0001)
        assertEquals(2.0, quota.extraUsage?.usedCredits ?: -1.0, 0.0001)
    }

    @Test
    fun `rejects a multiline cookie before network access`() = runTest {
        val malicious = credential.copy(
            accessToken = "session=valid\r\nAuthorization: Bearer injected"
        )

        val result = repository.validateCredential(malicious)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.AuthError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `maps rejected session to terminal authentication error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
    }

    private suspend fun CursorRepositoryImpl.validateAndFetchForTest(
        credential: Credential.ProviderSecretCredential
    ): Result<com.codexbar.android.core.domain.model.QuotaInfo, AppError> {
        `when`(prefsManager.loadCredential(AiService.CURSOR)).thenReturn(credential)
        return fetchQuota()
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun MockWebServer.takeRequestAt(index: Int): okhttp3.mockwebserver.RecordedRequest {
        var request = takeRequest()
        repeat(index) { request = takeRequest() }
        return request
    }
}
