package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.CodexResetCredits
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
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
                    Result.Success(
                        mapToQuotaInfo(
                            response = body,
                            resetCredits = fetchResetCreditsBestEffort(credential)
                        )
                    )
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
                            Result.Success(
                                mapToQuotaInfo(
                                    response = body,
                                    resetCredits = fetchResetCreditsBestEffort(refreshed)
                                )
                            )
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

    private suspend fun fetchResetCreditsBestEffort(
        credential: Credential.CodexCredential
    ): CodexResetCredits? {
        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(RESET_CREDITS_TIMEOUT_MILLIS) {
                    val response = apiService.getRateLimitResetCredits(
                        authorization = "Bearer ${credential.accessToken}",
                        accountId = credential.accountId
                    )
                    if (!response.isSuccessful) return@withTimeoutOrNull null
                    val body = response.body() ?: return@withTimeoutOrNull null
                    if (body.availableCount < 0) return@withTimeoutOrNull null

                    val now = Instant.now()
                    val availableExpirations = body.credits.mapNotNull { credit ->
                        if (credit.status != AVAILABLE_RESET_CREDIT_STATUS) return@mapNotNull null
                        credit.expiresAt
                            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                            ?.takeIf { it > now }
                    }.sorted()
                    val availableWithoutExpiry = body.credits.count { credit ->
                        credit.status == AVAILABLE_RESET_CREDIT_STATUS && credit.expiresAt == null
                    }
                    val derivedCount = availableExpirations.size + availableWithoutExpiry

                    CodexResetCredits(
                        availableCount = derivedCount,
                        expiresAt = availableExpirations
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun mapToQuotaInfo(
        response: CodexDto.UsageResponse,
        resetCredits: CodexResetCredits? = null
    ): QuotaInfo {
        val windows = buildList {
            response.rateLimit?.primaryWindow?.let { window ->
                add(mapRateLimitWindow("primary", window))
            }
            response.rateLimit?.secondaryWindow?.let { window ->
                add(mapRateLimitWindow("secondary", window))
            }
            addAll(mapAdditionalRateLimits(response.additionalRateLimits))
        }

        return QuotaInfo(
            service = AiService.CODEX,
            windows = windows,
            extraUsage = null,
            tier = response.planType?.replaceFirstChar { it.uppercase() },
            fetchedAt = Instant.now(),
            notices = availabilityNotices(response),
            codexResetCredits = resetCredits
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

    private fun mapAdditionalRateLimits(element: kotlinx.serialization.json.JsonElement?): List<UsageWindow> {
        val entries = element as? JsonArray ?: return emptyList()
        val usedLabels = mutableSetOf<String>()
        return entries.take(MAX_ADDITIONAL_RATE_LIMITS).flatMap { rawEntry ->
            val entry = rawEntry as? JsonObject ?: return@flatMap emptyList()
            val limitName = entry.stringOrNull("limit_name")
            val meteredFeature = entry.stringOrNull("metered_feature")
            val rateLimit = entry["rate_limit"] as? JsonObject ?: return@flatMap emptyList()
            val primary = rateLimit.rateLimitWindowOrNull("primary_window")
            val secondary = rateLimit.rateLimitWindowOrNull("secondary_window")
            val isSpark = sequenceOf(limitName, meteredFeature)
                .filterNotNull()
                .any { it.contains("spark", ignoreCase = true) }

            if (isSpark) {
                listOfNotNull(
                    primary?.let { window ->
                        val label = if ((window.limitWindowSeconds ?: 0L) >= SIX_DAYS_SECONDS) {
                            "Codex Spark Weekly"
                        } else {
                            "Codex Spark 5-Hour"
                        }
                        label to window
                    },
                    secondary?.let { window ->
                        val label = if ((window.limitWindowSeconds ?: 0L) <= SIX_HOURS_SECONDS) {
                            "Codex Spark 5-Hour"
                        } else {
                            "Codex Spark Weekly"
                        }
                        label to window
                    }
                ).mapNotNull { (label, window) ->
                    if (!usedLabels.add(label)) return@mapNotNull null
                    mapNamedRateLimitWindow(label, window)
                }
            } else {
                val window = primary ?: secondary ?: return@flatMap emptyList()
                val label = listOf(limitName, meteredFeature)
                    .firstOrNull { !it.isNullOrBlank() }
                    ?.trim()
                    ?.take(MAX_ADDITIONAL_RATE_LIMIT_LABEL_LENGTH)
                    ?: return@flatMap emptyList()
                if (!usedLabels.add(label)) return@flatMap emptyList()
                listOf(mapNamedRateLimitWindow(label, window))
            }
        }
    }

    private fun JsonObject.rateLimitWindowOrNull(key: String): CodexDto.RateLimitWindow? {
        val objectValue = this[key] as? JsonObject ?: return null
        val usedPercent = (objectValue["used_percent"] as? JsonPrimitive)
            ?.doubleOrNull
            ?: return null
        return CodexDto.RateLimitWindow(
            usedPercent = usedPercent,
            resetAt = (objectValue["reset_at"] as? JsonPrimitive)?.longOrNull,
            limitWindowSeconds = (objectValue["limit_window_seconds"] as? JsonPrimitive)?.longOrNull
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return (this[key] as? JsonPrimitive)?.contentOrNull
    }

    private fun mapNamedRateLimitWindow(
        label: String,
        window: CodexDto.RateLimitWindow
    ): UsageWindow {
        return UsageWindow(
            label = label,
            utilization = window.usedPercent / 100.0,
            resetsAt = window.resetAt?.takeIf { it > 0L }?.let(Instant::ofEpochSecond),
            windowDurationSeconds = window.limitWindowSeconds?.takeIf { it > 0L }
        )
    }

    companion object {
        private const val FIVE_HOURS_SECONDS = 5L * 60L * 60L
        private const val SIX_HOURS_SECONDS = 6L * 60L * 60L
        private const val SIX_DAYS_SECONDS = 6L * 24L * 60L * 60L
        private const val SEVEN_DAYS_SECONDS = 7L * 24L * 60L * 60L
        private const val RESET_CREDITS_TIMEOUT_MILLIS = 4_000L
        private const val AVAILABLE_RESET_CREDIT_STATUS = "available"
        private const val MAX_ADDITIONAL_RATE_LIMITS = 32
        private const val MAX_ADDITIONAL_RATE_LIMIT_LABEL_LENGTH = 80

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
