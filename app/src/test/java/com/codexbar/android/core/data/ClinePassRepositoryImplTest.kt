package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.clinepass.ClinePassApiService
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

class ClinePassRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: ClinePassRepositoryImpl

    private val credential = Credential.ProviderSecretCredential(
        service = AiService.CLINEPASS,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "cline-pass-key"
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
            .create(ClinePassApiService::class.java)
        repository = ClinePassRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps plan windows in stable order and sends bearer key`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLINEPASS)).thenReturn(credential)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "success": true,
                  "data": {
                    "limits": [
                      {"type":"monthly","percentUsed":67.5,"resetsAt":"2026-09-01T00:00:00Z"},
                      {"type":"five_hour","percentUsed":28.0,"resetsAt":"2026-08-13T14:00:00Z"},
                      {"type":"weekly","percentUsed":115.0,"resetsAt":null},
                      {"type":"future_window","percentUsed":10.0}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.CLINEPASS, quota.service)
        assertEquals(listOf("5-Hour", "7-Day", "Monthly"), quota.windows.map { it.label })
        assertEquals(0.28, quota.windows[0].utilization, 0.0001)
        assertEquals(1.0, quota.windows[1].utilization, 0.0001)
        assertEquals(0.675, quota.windows[2].utilization, 0.0001)

        val request = server.takeRequest()
        assertEquals("/api/v1/users/me/plan/usage-limits", request.path)
        assertEquals("Bearer cline-pass-key", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `rejects malformed key before network access`() = runTest {
        val result = repository.validateCredential(credential.copy(accessToken = "line one\nline two"))

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.AuthError)
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
