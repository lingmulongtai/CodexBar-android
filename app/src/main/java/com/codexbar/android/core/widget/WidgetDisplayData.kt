package com.codexbar.android.core.widget

import com.codexbar.android.core.domain.model.AiService

internal data class WidgetMetric(
    val label: String,
    val remaining: Float?,
    val reset: String?,
    val pace: String?,
    val resetAdvice: String? = null
) {
    val percent: String get() = widgetPercent(remaining)
    val shortLabel: String get() = widgetWindowLabel(label)
}

internal data class WidgetProvider(
    val service: AiService,
    val metrics: List<WidgetMetric>,
    val status: String?,
    val message: String,
    val age: String?
) {
    val name: String get() = if (service == AiService.COPILOT) "Copilot" else service.displayName
    val primary: WidgetMetric? get() = metrics.minByOrNull { it.remaining ?: Float.MAX_VALUE }
    val secondary: WidgetMetric? get() = metrics.firstOrNull { it != primary }
    val needsAttention: Boolean get() = status != null && status != "Fresh"
}

internal fun WidgetPrefsManager.displayData(config: WidgetDisplayConfig, waiting: String,
    now: Long = System.currentTimeMillis() / 1000): List<WidgetProvider> = config.services.map { service ->
    val updated = getCachedUpdatedAt(service) / 1000
    val ageMinutes = ((now - updated).coerceAtLeast(0) / 60)
    WidgetProvider(service,
        selectWidgetMetrics(getCachedLabels(service).map { label ->
            WidgetMetric(label, getCachedRemainingFraction(service, label),
                widgetCountdown(getCachedResetAt(service, label), now), getCachedPaceLabel(service, label), getCachedResetPlanLabel(service, label))
        }, config.maxRows), getCachedStatus(service), getCachedStatusMessage(service) ?: waiting,
        if (updated <= 0) null else when {
            ageMinutes >= 1440 -> "${ageMinutes / 1440}d"
            ageMinutes >= 60 -> "${ageMinutes / 60}h"
            else -> "${ageMinutes}m"
        })
}

internal fun selectWidgetMetrics(metrics: List<WidgetMetric>, maximum: Int): List<WidgetMetric> =
    metrics.sortedBy { it.remaining ?: Float.MAX_VALUE }.take(maximum.coerceAtLeast(1))
