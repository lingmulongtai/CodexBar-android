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
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.TokenRefreshAttemptDecision
import com.codexbar.android.core.security.TokenRefreshCoordinator
import com.codexbar.android.core.security.TokenRefreshRetryPolicy
import com.codexbar.android.core.security.TokenRefreshStateStore
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
    private val prefsManager: EncryptedPrefsManager,
    private val tokenRefreshCoordinator: TokenRefreshCoordinator,
    private val tokenRefreshStateStore: TokenRefreshStateStore
) : CoroutineWorker(context, workerParams) {

    private val retryPolicy = TokenRefreshRetryPolicy()

    override suspend fun doWork(): Result {
        val results = coroutineScope {
            AiService.entries
                .mapNotNull { service ->
                    prefsManager.loadCredential(service)?.let { credential -> service to credential }
                }
                .map { (service, credential) -> async { refreshIfDue(service, credential) } }
                .awaitAll()
        }

        return if (results.any { it.shouldRetryWork }) Result.retry() else Result.success()
    }

    private suspend fun refreshIfDue(service: AiService, credential: Credential): RefreshRunResult {
        val nowMillis = System.currentTimeMillis()
        val credentialFingerprint = tokenRefreshStateStore.fingerprintFor(service, credential)
        val previousState = tokenRefreshStateStore.load(service)
        return when (retryPolicy.decision(previousState, credentialFingerprint, nowMillis)) {
            TokenRefreshAttemptDecision.SkipTerminal,
            TokenRefreshAttemptDecision.SkipUntilDue -> RefreshRunResult.Skipped

            TokenRefreshAttemptDecision.Attempt -> {
                val outcome = refreshIfNeeded(credential)
                when (outcome) {
                    is RefreshOutcome.Success,
                    is RefreshOutcome.NotNeeded -> {
                        tokenRefreshStateStore.save(
                            service,
                            retryPolicy.success(
                                credentialFingerprint = credentialFingerprint,
                                nextAttemptAtMillis = nextRefreshDueMillis(credential, nowMillis)
                            )
                        )
                        RefreshRunResult.Succeeded
                    }

                    is RefreshOutcome.Failure -> {
                        tokenRefreshStateStore.save(
                            service,
                            retryPolicy.failure(
                                previousState = previousState,
                                credentialFingerprint = credentialFingerprint,
                                nowMillis = nowMillis,
                                terminal = outcome.terminal,
                                retryAtMillis = outcome.retryAtMillis
                            )
                        )
                        RefreshRunResult(shouldRetryWork = !outcome.terminal)
                    }
                }
            }
        }
    }

    private suspend fun refreshIfNeeded(credential: Credential): RefreshOutcome {
        return when (credential) {
            is Credential.ClaudeCredential -> refreshClaude(credential)
            is Credential.CodexCredential -> refreshCodex(credential)
            is Credential.GeminiCompanionCredential -> RefreshOutcome.NotNeeded
            is Credential.CopilotCredential -> RefreshOutcome.NotNeeded
        }
    }

    private suspend fun refreshClaude(credential: Credential.ClaudeCredential): RefreshOutcome {
        val expiresAt = credential.expiresAt ?: return RefreshOutcome.NotNeeded
        // Refresh if within 10 minutes of expiry
        if (Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) return RefreshOutcome.NotNeeded

        val refreshToken = credential.refreshToken ?: return RefreshOutcome.Failure(terminal = true)
        return try {
            val response = claudeTokenRefreshService.refreshToken(refreshToken = refreshToken)
            if (response.isSuccessful) {
                val body = response.body() ?: return RefreshOutcome.Failure()
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
                RefreshOutcome.Success
            } else {
                RefreshOutcome.Failure(terminal = response.code() == 400 || response.code() == 401)
            }
        } catch (_: Exception) {
            RefreshOutcome.Failure()
        }
    }

    private suspend fun refreshCodex(credential: Credential.CodexCredential): RefreshOutcome {
        return tokenRefreshCoordinator.withRefreshLock(AiService.CODEX) {
            val activeCredential = prefsManager.loadCredential(AiService.CODEX)
                as? Credential.CodexCredential
                ?: return@withRefreshLock RefreshOutcome.NotNeeded

            if (!activeCredential.matchesRefreshSubject(credential)) {
                return@withRefreshLock RefreshOutcome.NotNeeded
            }

            try {
                val request = CodexDto.TokenRefreshRequest(refreshToken = activeCredential.refreshToken)
                val response = codexTokenRefreshService.refreshToken(request)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@withRefreshLock RefreshOutcome.Failure()
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
                    RefreshOutcome.Success
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
                    RefreshOutcome.Failure(terminal = isTerminal)
                }
            } catch (_: Exception) {
                RefreshOutcome.Failure()
            }
        }
    }

    private fun Credential.CodexCredential.matchesRefreshSubject(
        other: Credential.CodexCredential
    ): Boolean {
        return refreshToken == other.refreshToken && accountId == other.accountId
    }

    private fun nextRefreshDueMillis(credential: Credential, nowMillis: Long): Long {
        val minimumDue = nowMillis + MIN_REFRESH_GAP_MILLIS
        return when (credential) {
            is Credential.ClaudeCredential -> credential.expiresAt
                ?.minusSeconds(REFRESH_BUFFER_SECONDS)
                ?.toEpochMilli()
                ?.coerceAtLeast(minimumDue)
                ?: (nowMillis + DEFAULT_PROACTIVE_REFRESH_MILLIS)

            is Credential.CodexCredential -> nowMillis + DEFAULT_PROACTIVE_REFRESH_MILLIS

            is Credential.GeminiCompanionCredential -> Long.MAX_VALUE

            is Credential.CopilotCredential -> Long.MAX_VALUE
        }
    }

    private data class RefreshRunResult(
        val shouldRetryWork: Boolean
    ) {
        companion object {
            val Skipped = RefreshRunResult(shouldRetryWork = false)
            val Succeeded = RefreshRunResult(shouldRetryWork = false)
        }
    }

    private sealed class RefreshOutcome {
        data object Success : RefreshOutcome()
        data object NotNeeded : RefreshOutcome()
        data class Failure(
            val terminal: Boolean = false,
            val retryAtMillis: Long? = null
        ) : RefreshOutcome()
    }

    companion object {
        /** Refresh buffer: refresh tokens that expire within this many seconds. */
        const val REFRESH_BUFFER_SECONDS = 600L // 10 minutes
        private const val MIN_REFRESH_GAP_MILLIS = 15 * 60 * 1000L
        private const val DEFAULT_PROACTIVE_REFRESH_MILLIS = 6 * 60 * 60 * 1000L
    }
}
