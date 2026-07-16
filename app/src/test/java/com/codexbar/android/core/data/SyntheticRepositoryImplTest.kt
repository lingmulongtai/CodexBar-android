package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.synthetic.SyntheticApiService
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

class SyntheticRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: SyntheticRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.SYNTHETIC,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "synthetic-key"
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
            .create(SyntheticApiService::class.java)
        repository = SyntheticRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps documented subscription quota and sends bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.SYNTHETIC)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "subscription": {
                    "limit": 135,
                    "requests": 27,
                    "renewsAt": "2026-07-21T14:36:14.288Z"
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.SYNTHETIC, quota.service)
        assertEquals("Subscription", quota.windows.single().label)
        assertEquals(0.2, quota.windows.single().utilization, 0.0001)
        assertEquals(1784644574L, quota.windows.single().resetsAt?.epochSecond)

        val request = server.takeRequest()
        assertEquals("/v2/quotas", request.path)
        assertEquals("Bearer synthetic-key", request.getHeader("Authorization"))
    }

    @Test
    fun `fails closed when quota payload is missing`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        val result = repository.validateCredential(credential)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.ParseError)
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
