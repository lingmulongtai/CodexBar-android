package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.fireworks.FireworksApiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

class FireworksRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: FireworksRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.FIREWORKS,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "fw-test-key",
        accountReference = "acct-1_x.d"
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
            .create(FireworksApiService::class.java)
        repository = FireworksRepositoryImpl(
            apiService = apiService,
            prefsManager = prefsManager,
            clock = Clock.fixed(Instant.parse("2026-08-13T07:00:00Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sums first currency spend and sends a bounded request`() = runTest {
        `when`(prefsManager.loadCredential(AiService.FIREWORKS)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "lineItems": [
                    {"totalCost":{"currencyCode":"USD","nanos":492256016,"units":"0"}},
                    {"totalCost":{"currencyCode":"EUR","nanos":900000000,"units":"9"}},
                    {"totalCost":{"currencyCode":"USD","nanos":33292280,"units":"1"}}
                  ],
                  "usageBuckets": []
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.FIREWORKS, quota.service)
        assertEquals("Last 30 days: USD 1.525548", quota.tier)
        assertTrue(quota.windows.isEmpty())

        val request = server.takeRequest()
        assertEquals("Bearer fw-test-key", request.getHeader("Authorization"))
        assertEquals("/v1/accounts/acct-1_x.d/billing/summary", request.requestUrl?.encodedPath)
        assertEquals("2026-07-14T07:00:00Z", request.requestUrl?.queryParameter("startTime"))
        assertEquals("2026-08-13T07:00:00Z", request.requestUrl?.queryParameter("endTime"))
    }

    @Test
    fun `rejects malformed account slug before network access`() = runTest {
        val badSlugs = listOf("sp ace", "has/slash", "has?query", "percent%2F", "coléon")

        badSlugs.forEach { slug ->
            val result = repository.validateCredential(credential.copy(accountReference = slug))
            assertTrue(result is Result.Failure)
            assertTrue((result as Result.Failure).error is AppError.AuthError)
        }
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
