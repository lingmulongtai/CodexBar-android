package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.QuotaNotice
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.RetryAfter
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.TokenRefreshCoordinator
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class CodexRepositoryImpl @Inject constructor(
    private val apiService: CodexApiService,
    private val tokenRefreshService: CodexTokenRefreshService,
    private val prefsManager: EncryptedPrefsManager,
    private val tokenRefreshCoordinator: TokenRefreshCoordinator
) : QuotaRepository {

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.CODEX)
            as? Credential.CodexCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.CODEX))

        return try {
            val response = apiService.getUsage(
                authorization = "Bearer ${credential.accessToken}",
                accountId = credential.accountId
            )

            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty response body"))
                    Result.Success(mapToQuotaInfo(body))
                }
                401 -> {
                    val refreshed = refreshToken(credential)
                    if (refreshed != null) {
                        val retryResponse = apiService.getUsage(
                            authorization = "Bearer ${refreshed.accessToken}",
                            accountId = refreshed.accountId
                        )
                        if (retryResponse.isSuccessful) {
                            val body = retryResponse.body()
                                ?: return Result.Failure(AppError.ParseError("Empty response body"))
                            Result.Success(mapToQuotaInfo(body))
                        } else {
                            Result.Failure(AppError.AuthError(AiService.CODEX, isTerminal = true))
                        }
                    } else {
                        Result.Failure(AppError.AuthError(AiService.CODEX, isTerminal = true))
                    }
                }
                429 -> Result.Failure(AppError.RateLimited(RetryAfter.parseRetryAt(response.headers()["Retry-After"])))
                else -> Result.Failure(
                    AppError.NetworkError("HTTP ${response.code()}: ${response.message()}")
                )
            }
        } catch (e: IOException) {
            Result.Failure(AppError.NetworkError(e.message ?: "Network error", e))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError(e.message ?: "Parse error", e))
        }
    }

    override suspend fun validateCredential(): Result<Unit, AppError> {
        return when (val result = fetchQuota()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }

    override suspend fun validateCredential(credential: Credential): Result<Unit, AppError> {
        val typed = credential as? Credential.CodexCredential
            ?: return Result.Failure(AppError.AuthError(AiService.CODEX, isTerminal = true))

        return try {
            val response = apiService.getUsage(
                authorization = "Bearer ${typed.accessToken}",
                accountId = typed.accountId
            )
            when (response.code()) {
                200 -> Result.Success(Unit)
                401, 403 -> Result.Failure(AppError.AuthError(AiService.CODEX, isTerminal = true))
                429 -> Result.Failure(AppError.RateLimited(RetryAfter.parseRetryAt(response.headers()["Retry-After"])))
                else -> Result.Failure(AppError.NetworkError("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: IOException) {
            Result.Failure(AppError.NetworkError(e.message ?: "Network error", e))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError(e.message ?: "Parse error", e))
        }
    }

    private suspend fun refreshToken(credential: Credential.CodexCredential): Credential.CodexCredential? {
        return tokenRefreshCoordinator.withRefreshLock(AiService.CODEX) {
            val activeCredential = prefsManager.loadCredential(AiService.CODEX)
                as? Credential.CodexCredential
                ?: return@withRefreshLock null

            if (!activeCredential.matchesRefreshSubject(credential)) {
                return@withRefreshLock activeCredential
            }

            try {
                val request = CodexDto.TokenRefreshRequest(refreshToken = activeCredential.refreshToken)
                val response = tokenRefreshService.refreshToken(request)

                if (response.isSuccessful) {
                    val body = response.body() ?: return@withRefreshLock null
                    val newCredential = Credential.CodexCredential(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: activeCredential.refreshToken,
                        accountId = activeCredential.accountId
                    )
                    val currentCredential = prefsManager.loadCredential(AiService.CODEX)
                        as? Credential.CodexCredential
                    if (currentCredential?.matchesRefreshSubject(activeCredential) == true) {
                        prefsManager.saveCredential(AiService.CODEX, newCredential)
                        newCredential
                    } else {
                        currentCredential
                    }
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
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun Credential.CodexCredential.matchesRefreshSubject(
        other: Credential.CodexCredential
    ): Boolean {
        return refreshToken == other.refreshToken && accountId == other.accountId
    }

    private fun mapToQuotaInfo(response: CodexDto.UsageResponse): QuotaInfo {
        val windows = buildList {
            response.rateLimit?.primaryWindow?.let { window ->
                add(mapRateLimitWindow("primary", window))
            }
            response.rateLimit?.secondaryWindow?.let { window ->
                add(mapRateLimitWindow("secondary", window))
            }
        }

        return QuotaInfo(
            service = AiService.CODEX,
            windows = windows,
            extraUsage = null,
            tier = response.planType?.replaceFirstChar { it.uppercase() },
            fetchedAt = Instant.now(),
            notices = availabilityNotices(response)
        )
    }

    private fun availabilityNotices(response: CodexDto.UsageResponse): Set<QuotaNotice> {
        val knownDurations = listOfNotNull(
            response.rateLimit?.primaryWindow?.limitWindowSeconds,
            response.rateLimit?.secondaryWindow?.limitWindowSeconds
        ).filter { it > 0L }
        val hasLongTermWindow = knownDurations.any { it >= SEVEN_DAYS_SECONDS }
        val hasFiveHourOrTighterWindow = knownDurations.any { it in 1L..FIVE_HOURS_SECONDS }

        return if (hasLongTermWindow && !hasFiveHourOrTighterWindow) {
            setOf(QuotaNotice.WindowLimitNotProvided(FIVE_HOURS_SECONDS))
        } else {
            emptySet()
        }
    }

    private fun mapRateLimitWindow(type: String, window: CodexDto.RateLimitWindow): UsageWindow {
        val label = when (window.limitWindowSeconds) {
            FIVE_HOURS_SECONDS -> "5-Hour"
            SEVEN_DAYS_SECONDS -> "7-Day"
            else -> {
                val seconds = window.limitWindowSeconds ?: 0L
                if (seconds > 0) "${seconds / 3600}h" else type.replaceFirstChar { it.uppercase() }
            }
        }

        return UsageWindow(
            label = label,
            utilization = window.usedPercent / 100.0,
            resetsAt = window.resetAt?.let { Instant.ofEpochSecond(it) },
            windowDurationSeconds = window.limitWindowSeconds?.takeIf { it > 0L }
        )
    }

    companion object {
        private const val FIVE_HOURS_SECONDS = 5L * 60L * 60L
        private const val SEVEN_DAYS_SECONDS = 7L * 24L * 60L * 60L

        fun parseBalance(element: kotlinx.serialization.json.JsonElement?): Double? {
            if (element == null) return null
            return when (element) {
                is JsonPrimitive -> {
                    element.doubleOrNull ?: element.content.toDoubleOrNull()
                }
                else -> null
            }
        }
    }
}
