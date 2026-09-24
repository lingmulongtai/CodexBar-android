package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.network.devin.DevinApiService
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class DevinRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var repository: DevinRepositoryImpl
    private val now = Instant.parse("2026-09-25T09:00:00Z")
    private val credential = Credential.ProviderSecretCredential(AiService.DEVIN,
        ProviderSecretKind.API_KEY, "cog_test_token", accountReference = "org-test-1")

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        prefs = mock(EncryptedPrefsManager::class.java)
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(DevinApiService::class.java)
        repository = DevinRepositoryImpl(api, prefs, Clock.fixed(now, ZoneOffset.UTC))
        kotlinx.coroutines.runBlocking { `when`(prefs.loadCredential(AiService.DEVIN)).thenReturn(credential) }
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `uses read only organization API with thirty day bounds and no invented quota`() = runTest {
        server.enqueue(MockResponse().setBody("""{"total_acus":123.4,"consumption_by_date":[]}"""))
        val result = repository.fetchQuota() as Result.Success
        assertEquals("30d · 123.40 ACU used", result.value.tier)
        assertTrue(result.value.windows.isEmpty())
        assertNull(result.value.extraUsage)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v3/organizations/org-test-1/consumption/daily", request.requestUrl?.encodedPath)
        assertEquals("Bearer cog_test_token", request.getHeader("Authorization"))
        assertEquals((now.epochSecond - 30 * 86400).toString(), request.requestUrl?.queryParameter("time_after"))
        assertEquals(now.epochSecond.toString(), request.requestUrl?.queryParameter("time_before"))
    }

    @Test fun `permission failure retains credentials and differs from an expired token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val denied = (repository.fetchQuota() as Result.Failure).error as AppError.AuthError
        assertTrue(denied.permissionDenied)
        assertFalse(denied.isTerminal)
        server.enqueue(MockResponse().setResponseCode(401))
        val expired = (repository.fetchQuota() as Result.Failure).error as AppError.AuthError
        assertTrue(expired.isTerminal)
        assertFalse(expired.permissionDenied)
    }

    @Test fun `rejects path injection and malformed credentials without network access`() = runTest {
        listOf("bad/path", "../org", "bad%2Fpath", "bad?query", "").forEach { org ->
            assertTrue(repository.validateCredential(credential.copy(accountReference = org)) is Result.Failure)
        }
        assertTrue(repository.validateCredential(credential.copy(accessToken = "cog_bad\r\nkey")) is Result.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test fun `invalid consumption is an error rather than zero usage`() = runTest {
        listOf("{}", "{\"total_acus\":-1}", "{\"total_acus\":null}").forEach { body ->
            server.enqueue(MockResponse().setBody(body))
            assertTrue((repository.fetchQuota() as Result.Failure).error is AppError.ParseError)
        }
    }

    @Test fun `rate limits retain retry time and service outages stay retryable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "60"))
        assertTrue((repository.fetchQuota() as Result.Failure).error is AppError.RateLimited)
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(AppError.ServiceUnavailable, (repository.fetchQuota() as Result.Failure).error)
    }
}
