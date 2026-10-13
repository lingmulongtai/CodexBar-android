package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import androidx.work.impl.utils.SerialExecutorImpl
import com.codexbar.android.core.data.QuotaResetHistoryStore
import com.codexbar.android.core.data.QuotaResetMonitor
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.ConnectionHealthStore
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.TokenRefreshCoordinator
import com.codexbar.android.core.security.TokenRefreshStateStore
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import retrofit2.Response
import java.time.Instant
import java.util.concurrent.Executor

class TokenRefreshWorkerTest {
    private lateinit var color: org.mockito.MockedStatic<android.graphics.Color>
    @Before fun setup() { color = org.mockito.Mockito.mockStatic(android.graphics.Color::class.java) }
    @After fun tearDown() { color.close() }

    private val expired = Credential.CodexCredential("old-access", "old-refresh", "same-account",
        expiresAt = Instant.now().minusSeconds(60))

    @Test fun `Windows switch during credential load prevents background OAuth request`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        val health = mock(ConnectionHealthStore::class.java)
        val state = mock(TokenRefreshStateStore::class.java)
        `when`(state.fingerprintFor(AiService.CODEX, expired)).thenReturn("old-fingerprint")
        var loads = 0
        `when`(prefs.loadCredential(AiService.CODEX)).thenAnswer {
            if (++loads == 2) `when`(prefs.usesDesktopBridge(AiService.CODEX)).thenReturn(true)
            expired
        }
        var requests = 0
        val api = object : CodexTokenRefreshService {
            override suspend fun refreshToken(request: CodexDto.TokenRefreshRequest): Response<CodexDto.TokenRefreshResponse> {
                requests++
                error("OAuth must not run after the Windows switch")
            }
        }
        worker(api, prefs, health, state).doWork()
        assertEquals(2, loads)
        assertEquals(0, requests)
        assertEquals(0, mockingDetails(health).invocations.size)
        assertEquals(0, mockingDetails(state).invocations.count { it.method.name == "save" })
    }

    @Test fun `old terminal OAuth failure cannot mark a reconnected same account unhealthy`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        val health = mock(ConnectionHealthStore::class.java)
        val state = mock(TokenRefreshStateStore::class.java)
        `when`(state.fingerprintFor(AiService.CODEX, expired)).thenReturn("old-fingerprint")
        var current = expired
        `when`(prefs.loadCredential(AiService.CODEX)).thenAnswer { current }
        var requests = 0
        val api = object : CodexTokenRefreshService {
            override suspend fun refreshToken(request: CodexDto.TokenRefreshRequest): Response<CodexDto.TokenRefreshResponse> {
                requests++
                current = expired.copy(accessToken = "new-access", refreshToken = "new-refresh")
                return Response.error(401, "{\"error\":\"invalid_grant\"}".toResponseBody("application/json".toMediaType()))
            }
        }
        worker(api, prefs, health, state).doWork()
        assertEquals(1, requests)
        assertEquals("new-refresh", current.refreshToken)
        assertEquals(0, mockingDetails(health).invocations.size)
        assertEquals(0, mockingDetails(state).invocations.count { it.method.name == "save" })
    }

    private fun worker(api: CodexTokenRefreshService, prefs: EncryptedPrefsManager,
        health: ConnectionHealthStore, state: TokenRefreshStateStore): TokenRefreshWorker {
        val monitor = QuotaResetMonitor(mock(QuotaResetHistoryStore::class.java), prefs,
            mock(QuotaNotificationService::class.java))
        val parameters = mock(WorkerParameters::class.java)
        val executor = mock(TaskExecutor::class.java)
        `when`(parameters.taskExecutor).thenReturn(executor)
        `when`(executor.serialTaskExecutor).thenReturn(SerialExecutorImpl(Executor { it.run() }))
        return TokenRefreshWorker(mock(Context::class.java), parameters,
            api, prefs, health, TokenRefreshCoordinator(), state, monitor)
    }
}
