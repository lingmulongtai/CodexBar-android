package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.gemini.GeminiDto
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.TokenRefreshCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val claudeTokenRefreshService: ClaudeTokenRefreshService,
    private val codexTokenRefreshService: CodexTokenRefreshService,
    private val geminiTokenRefreshService: GeminiTokenRefreshService,
    private val prefsManager: EncryptedPrefsManager,
    private val tokenRefreshCoordinator: TokenRefreshCoordinator
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val results = coroutineScope {
            AiService.entries
                .mapNotNull { service ->
                    prefsManager.loadCredential(service)?.let { credential -> service to credential }
                }
                .map { (_, credential) -> async { refreshIfNeeded(credential) } }
                .awaitAll()
        }

        // If all services had no credential or succeeded, it's a success.
        // If any failed, retry with backoff.
        return if (results.all { it }) Result.success() else Result.retry()
    }

    /**
     * Returns true if refresh was not needed or succeeded, false if refresh failed.
     */
    private suspend fun refreshIfNeeded(credential: Credential): Boolean {
        return when (credential) {
            is Credential.ClaudeCredential -> refreshClaude(credential)
            is Credential.CodexCredential -> refreshCodex(credential)
            is Credential.GeminiCredential -> refreshGemini(credential)
            is Credential.CopilotCredential -> true
        }
    }

    private suspend fun refreshClaude(credential: Credential.ClaudeCredential): Boolean {
        val expiresAt = credential.expiresAt ?: return true
        // Refresh if within 10 minutes of expiry
        if (Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) return true

        val refreshToken = credential.refreshToken ?: return false
        return try {
            val response = claudeTokenRefreshService.refreshToken(refreshToken = refreshToken)
            if (response.isSuccessful) {
                val body = response.body() ?: return false
                prefsManager.saveCredential(
                    AiService.CLAUDE,
                    Credential.ClaudeCredential(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: refreshToken,
                        expiresAt = Instant.now().plusSeconds(body.expiresIn.toLong()),
                        scopes = credential.scopes,
                        rateLimitTier = credential.rateLimitTier
                    )
                )
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun refreshCodex(credential: Credential.CodexCredential): Boolean {
        // Codex has no expiry field — always attempt a proactive refresh
        return tokenRefreshCoordinator.withRefreshLock(AiService.CODEX) {
            val activeCredential = prefsManager.loadCredential(AiService.CODEX)
                as? Credential.CodexCredential
                ?: return@withRefreshLock true

            if (!activeCredential.matchesRefreshSubject(credential)) {
                return@withRefreshLock true
            }

            try {
                val request = CodexDto.TokenRefreshRequest(refreshToken = activeCredential.refreshToken)
                val response = codexTokenRefreshService.refreshToken(request)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@withRefreshLock false
                    val newCredential = Credential.CodexCredential(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: activeCredential.refreshToken,
                        accountId = activeCredential.accountId
                    )
                    val currentCredential = prefsManager.loadCredential(AiService.CODEX)
                        as? Credential.CodexCredential
                    if (currentCredential?.matchesRefreshSubject(activeCredential) == true) {
                        prefsManager.saveCredential(AiService.CODEX, newCredential)
                    }
                    true
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    val isTerminal = CodexDto.TERMINAL_ERROR_CODES.any { errorBody.contains(it) }
                    if (isTerminal) {
                        val currentCredential = prefsManager.loadCredential(AiService.CODEX)
                            as? Credential.CodexCredential
                        if (currentCredential?.matchesRefreshSubject(activeCredential) == true) {
                            prefsManager.deleteCredential(AiService.CODEX)
                        }
                    }
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun Credential.CodexCredential.matchesRefreshSubject(
        other: Credential.CodexCredential
    ): Boolean {
        return refreshToken == other.refreshToken && accountId == other.accountId
    }

    private suspend fun refreshGemini(credential: Credential.GeminiCredential): Boolean {
        // Refresh if within 10 minutes of expiry
        if (System.currentTimeMillis() < credential.expiresAtMs - REFRESH_BUFFER_SECONDS * 1000) return true

        return try {
            val request = GeminiDto.TokenRefreshRequest(
                refreshToken = credential.refreshToken,
                clientId = credential.oauthClientId,
                clientSecret = credential.oauthClientSecret
            )
            val response = geminiTokenRefreshService.refreshToken(request)
            if (response.isSuccessful) {
                val body = response.body() ?: return false
                val expiresIn = body.expiresIn ?: 3600
                prefsManager.saveCredential(
                    AiService.GEMINI,
                    Credential.GeminiCredential(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: credential.refreshToken,
                        expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L),
                        oauthClientId = credential.oauthClientId,
                        oauthClientSecret = credential.oauthClientSecret
                    )
                )
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** Refresh buffer: refresh tokens that expire within this many seconds. */
        const val REFRESH_BUFFER_SECONDS = 600L // 10 minutes
    }
}
