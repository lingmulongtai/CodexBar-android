package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.gemini.GeminiCompanionAuthenticationException
import com.codexbar.android.core.network.gemini.GeminiCompanionClient
import com.codexbar.android.core.network.gemini.GeminiCompanionSnapshot
import com.codexbar.android.core.network.gemini.GeminiCompanionWindow
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GeminiRepositoryImplTest {
    private val credential = Credential.GeminiCompanionCredential(
        host = "127.0.0.1",
        port = 43821,
        companionId = "5b017391-6dc4-4ab7-b0ad-2255dada62d7",
        sharedKeyBase64Url = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    )
    private var companionSnapshot: GeminiCompanionSnapshot? = null
    private var companionFailure: Throwable? = null
    private val companionClient = object : GeminiCompanionClient(Json) {
        override suspend fun fetchSnapshot(
            credential: Credential.GeminiCompanionCredential,
            now: java.time.Instant
        ): GeminiCompanionSnapshot {
            companionFailure?.let { throw it }
            return requireNotNull(companionSnapshot)
        }
    }
    private val prefsManager = mock(EncryptedPrefsManager::class.java)
    private val repository = GeminiRepositoryImpl(companionClient, prefsManager)

    @Test
    fun `fetch maps sanitized companion snapshot to Gemini quota`() = runTest {
        val generatedAt = 1_750_000_000L
        `when`(prefsManager.loadCredential(AiService.GEMINI)).thenReturn(credential)
        companionSnapshot = GeminiCompanionSnapshot(
            schemaVersion = 1,
            source = "gemini-cli-terminal",
            generatedAtEpochSeconds = generatedAt,
            cliVersion = "0.50.0",
            tier = "Google AI Pro",
            windows = listOf(
                GeminiCompanionWindow("Pro", 0.25, generatedAt + 5_400),
                GeminiCompanionWindow("Flash", 0.10, generatedAt + 7_200)
            )
        )

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.GEMINI, quota.service)
        assertEquals("Google AI Pro", quota.tier)
        assertEquals(2, quota.windows.size)
        assertEquals(0.25, quota.windows.first().utilization, 0.001)
        assertEquals(generatedAt, quota.fetchedAt.epochSecond)
    }

    @Test
    fun `wrong companion key requires re-pairing`() = runTest {
        `when`(prefsManager.loadCredential(AiService.GEMINI)).thenReturn(credential)
        companionFailure = GeminiCompanionAuthenticationException("authentication failed")

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError)
        assertTrue((error as AppError.AuthError).isTerminal)
    }

    @Test
    fun `fetch requires a companion pairing`() = runTest {
        `when`(prefsManager.loadCredential(AiService.GEMINI)).thenReturn(null)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }
}
