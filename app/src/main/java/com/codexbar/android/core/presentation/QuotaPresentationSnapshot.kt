package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import java.time.Instant

data class QuotaPresentationSnapshot(
    val generatedAt: Instant,
    val services: List<ServiceQuotaPresentation>,
    val source: RefreshSourcePresentation = RefreshSourcePresentation.Unknown,
    val privacy: PrivacyPresentation = PrivacyPresentation()
)

data class ServiceQuotaPresentation(
    val service: AiService,
    val accountLabel: String?,
    val tier: String?,
    val status: ServiceQuotaStatus,
    val primaryMetric: QuotaMetricPresentation?,
    val metrics: List<QuotaMetricPresentation>,
    val extraUsage: ExtraUsagePresentation?,
    val freshness: FreshnessPresentation,
    val supportedActions: Set<QuotaAction>
)

data class QuotaMetricPresentation(
    val id: String,
    val label: String,
    val usedFraction: Double?,
    val remainingFraction: Double?,
    val usedPercent: Int?,
    val remainingPercent: Int?,
    val usedLabel: String,
    val remainingLabel: String,
    val barProgress: Float,
    val severity: QuotaSeverity,
    val resetsAt: Instant?,
    val resetLabel: String?,
    val pace: PacePresentation
)

data class ExtraUsagePresentation(
    val label: String,
    val usedCreditsLabel: String,
    val limitLabel: String,
    val remainingLabel: String,
    val utilizationFraction: Double,
    val severity: QuotaSeverity
)

data class FreshnessPresentation(
    val fetchedAt: Instant?,
    val ageLabel: String,
    val state: FreshnessState,
    val staleReason: String? = null,
    val nextRetryAt: Instant? = null
)

data class PacePresentation(
    val state: PaceState,
    val label: String,
    val cycleProgressLabel: String? = null,
    val usageRateLabel: String? = null,
    val paceMultiplierLabel: String? = null,
    val reserveLabel: String? = null,
    val forecastLabel: String? = null
)

data class PrivacyPresentation(
    val redactSensitiveValues: Boolean = false,
    val lockScreenRedacted: Boolean = false,
    val widgetRedacted: Boolean = false
)

sealed class RefreshSourcePresentation {
    data object Unknown : RefreshSourcePresentation()
    data class Trigger(val name: String) : RefreshSourcePresentation()
}

enum class ServiceQuotaStatus {
    Fresh,
    Stale,
    Loading,
    AuthRequired,
    RateLimited,
    Offline,
    ProviderError,
    Disconnected,
    Redacted
}

enum class FreshnessState {
    Fresh,
    Stale,
    Unknown,
    Error,
    RateLimited
}

enum class PaceState {
    Unknown,
    CollectingHistory,
    OnTrack,
    AtRisk,
    Exhausting
}

enum class QuotaSeverity {
    Good,
    Warning,
    Critical,
    Unknown,
    Redacted
}

enum class QuotaAction {
    OpenDashboard,
    Refresh,
    StartMonitoring,
    StopMonitoring,
    Reauthenticate,
    Disconnect,
    ConfigureWidget
}
