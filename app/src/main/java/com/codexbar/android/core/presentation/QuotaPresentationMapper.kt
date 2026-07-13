package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.ExtraUsage
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

class QuotaPresentationMapper(
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun map(
        quotas: List<QuotaInfo>,
        errors: Map<AiService, AppError> = emptyMap(),
        generatedAt: Instant = clock.instant(),
        locale: Locale = Locale.getDefault(),
        privacy: PrivacyPresentation = PrivacyPresentation(),
        source: RefreshSourcePresentation = RefreshSourcePresentation.Unknown,
        paceByMetricKey: Map<String, PacePresentation> = emptyMap()
    ): QuotaPresentationSnapshot {
        val successfulServices = quotas.map { quota ->
            val metrics = quota.windows.mapIndexed { index, window ->
                mapWindow(
                    service = quota.service,
                    window = window,
                    index = index,
                    generatedAt = generatedAt,
                    locale = locale,
                    privacy = privacy,
                    paceByMetricKey = paceByMetricKey
                )
            }
            val primary = metrics.maxWithOrNull(
                compareBy<QuotaMetricPresentation> { it.usedFraction ?: -1.0 }
                    .thenBy { it.label }
            )
            ServiceQuotaPresentation(
                service = quota.service,
                accountLabel = null,
                tier = if (privacy.redactSensitiveValues) null else quota.tier,
                status = if (privacy.redactSensitiveValues) ServiceQuotaStatus.Redacted else ServiceQuotaStatus.Fresh,
                primaryMetric = primary,
                metrics = metrics,
                extraUsage = quota.extraUsage?.let { mapExtraUsage(it, locale, privacy) },
                freshness = FreshnessPresentation(
                    fetchedAt = quota.fetchedAt,
                    ageLabel = formatAge(quota.fetchedAt, generatedAt),
                    state = FreshnessState.Fresh
                ),
                supportedActions = setOf(
                    QuotaAction.OpenDashboard,
                    QuotaAction.Refresh,
                    QuotaAction.StartMonitoring,
                    QuotaAction.Disconnect
                )
            )
        }

        val failedServices = errors
            .filterKeys { service -> successfulServices.none { it.service == service } }
            .map { (service, error) ->
                mapError(service, error, generatedAt)
            }

        val services = (successfulServices + failedServices)
            .sortedWith(
                compareByDescending<ServiceQuotaPresentation> {
                    it.primaryMetric?.usedFraction ?: -1.0
                }.thenBy { it.service.ordinal }
            )

        return QuotaPresentationSnapshot(
            generatedAt = generatedAt,
            services = services,
            source = source,
            privacy = privacy
        )
    }

    private fun mapWindow(
        service: AiService,
        window: UsageWindow,
        index: Int,
        generatedAt: Instant,
        locale: Locale,
        privacy: PrivacyPresentation,
        paceByMetricKey: Map<String, PacePresentation>
    ): QuotaMetricPresentation {
        val used = window.utilization.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        val remaining = used?.let { 1.0 - it }
        val usedPercent = used?.toPercent()
        val remainingPercent = remaining?.toPercent()
        val severity = when {
            privacy.redactSensitiveValues -> QuotaSeverity.Redacted
            used == null -> QuotaSeverity.Unknown
            used >= CRITICAL_USED_FRACTION -> QuotaSeverity.Critical
            used >= WARNING_USED_FRACTION -> QuotaSeverity.Warning
            else -> QuotaSeverity.Good
        }
        val label = window.label.ifBlank { "Window ${index + 1}" }
        return QuotaMetricPresentation(
            id = label.lowercase(locale).replace(Regex("[^a-z0-9]+"), "-").trim('-')
                .ifBlank { "window-${index + 1}" },
            label = label,
            usedFraction = if (privacy.redactSensitiveValues) null else used,
            remainingFraction = if (privacy.redactSensitiveValues) null else remaining,
            usedPercent = if (privacy.redactSensitiveValues) null else usedPercent,
            remainingPercent = if (privacy.redactSensitiveValues) null else remainingPercent,
            usedLabel = if (privacy.redactSensitiveValues || usedPercent == null) "Used hidden" else "$usedPercent% used",
            remainingLabel = if (privacy.redactSensitiveValues || remainingPercent == null) {
                "Remaining hidden"
            } else {
                "$remainingPercent% left"
            },
            barProgress = if (privacy.redactSensitiveValues) 0f else (remaining ?: 0.0).toFloat(),
            severity = severity,
            resetsAt = if (privacy.redactSensitiveValues) null else window.resetsAt,
            resetLabel = if (privacy.redactSensitiveValues) null else window.resetsAt?.let {
                formatResetLabel(it, generatedAt)
            },
            pace = paceByMetricKey[metricKey(service, label)] ?: PacePresentation(
                state = PaceState.CollectingHistory,
                label = "Collecting pace history"
            )
        )
    }

    private fun mapExtraUsage(
        extraUsage: ExtraUsage,
        locale: Locale,
        privacy: PrivacyPresentation
    ): ExtraUsagePresentation {
        val used = extraUsage.usedCredits.coerceAtLeast(0.0)
        val limit = extraUsage.monthlyLimit.coerceAtLeast(0.0)
        val remaining = (limit - used).coerceAtLeast(0.0)
        val utilization = extraUsage.utilization.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        return ExtraUsagePresentation(
            label = "Credits",
            usedCreditsLabel = if (privacy.redactSensitiveValues) {
                "Used hidden"
            } else {
                "${extraUsage.currency} ${String.format(locale, "%.2f", used)} used"
            },
            limitLabel = if (privacy.redactSensitiveValues) {
                "Limit hidden"
            } else {
                "${extraUsage.currency} ${String.format(locale, "%.2f", limit)} limit"
            },
            remainingLabel = if (privacy.redactSensitiveValues) {
                "Remaining hidden"
            } else {
                "${extraUsage.currency} ${String.format(locale, "%.2f", remaining)} left"
            },
            utilizationFraction = if (privacy.redactSensitiveValues) 0.0 else utilization,
            severity = when {
                privacy.redactSensitiveValues -> QuotaSeverity.Redacted
                utilization >= CRITICAL_USED_FRACTION -> QuotaSeverity.Critical
                utilization >= WARNING_USED_FRACTION -> QuotaSeverity.Warning
                else -> QuotaSeverity.Good
            }
        )
    }

    private fun mapError(
        service: AiService,
        error: AppError,
        generatedAt: Instant
    ): ServiceQuotaPresentation {
        val status = when (error) {
            is AppError.AuthError -> if (error.isTerminal) ServiceQuotaStatus.AuthRequired else ServiceQuotaStatus.ProviderError
            is AppError.CredentialNotFound -> ServiceQuotaStatus.Disconnected
            is AppError.NetworkError -> ServiceQuotaStatus.Offline
            is AppError.RateLimited -> ServiceQuotaStatus.RateLimited
            is AppError.ParseError,
            AppError.ServiceUnavailable -> ServiceQuotaStatus.ProviderError
        }
        val freshnessState = when (error) {
            is AppError.RateLimited -> FreshnessState.RateLimited
            else -> FreshnessState.Error
        }
        return ServiceQuotaPresentation(
            service = service,
            accountLabel = null,
            tier = null,
            status = status,
            primaryMetric = null,
            metrics = emptyList(),
            extraUsage = null,
            freshness = FreshnessPresentation(
                fetchedAt = null,
                ageLabel = "No fresh data",
                state = freshnessState,
                staleReason = error.toPresentationMessage(),
                nextRetryAt = (error as? AppError.RateLimited)?.retryAt
            ),
            supportedActions = setOf(
                QuotaAction.OpenDashboard,
                QuotaAction.Refresh,
                QuotaAction.Reauthenticate,
                QuotaAction.Disconnect
            )
        )
    }

    private fun AppError.toPresentationMessage(): String {
        return when (this) {
            is AppError.AuthError -> if (isTerminal) "Reauthentication required" else "Authentication failed"
            is AppError.CredentialNotFound -> "Not connected"
            is AppError.NetworkError -> "Network unavailable"
            is AppError.RateLimited -> retryAt?.let { "Rate limited until $it" } ?: "Rate limited"
            is AppError.ParseError -> "Provider response could not be parsed"
            AppError.ServiceUnavailable -> "Provider unavailable"
        }
    }

    private fun formatAge(fetchedAt: Instant?, now: Instant): String {
        if (fetchedAt == null) return "No fresh data"
        val age = Duration.between(fetchedAt, now)
        if (age.isNegative || age.toMinutes() < 1) return "just now"
        return when {
            age.toMinutes() < 60 -> "${age.toMinutes()}m ago"
            age.toHours() < 24 -> "${age.toHours()}h ago"
            else -> "${age.toDays()}d ago"
        }
    }

    private fun formatResetLabel(resetsAt: Instant, now: Instant): String? {
        val duration = Duration.between(now, resetsAt)
        if (duration.isNegative || duration.isZero) return null
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()
        return when {
            days > 0 -> "Resets in ${days}d ${hours % 24}h"
            hours > 0 -> "Resets in ${hours}h ${minutes % 60}m"
            else -> "Resets in ${minutes}m"
        }
    }

    private fun Double.toPercent(): Int = (this * 100.0).roundToInt().coerceIn(0, 100)

    companion object {
        fun metricKey(service: AiService, label: String): String = "${service.name}|$label"

        private const val WARNING_USED_FRACTION = 0.60
        private const val CRITICAL_USED_FRACTION = 0.85
    }
}
