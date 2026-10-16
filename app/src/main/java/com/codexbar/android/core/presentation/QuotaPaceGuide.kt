package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import java.time.ZoneId

/** Time-proportional remaining quota, never an extra allowance or a billing estimate. */
data class QuotaPaceGuide(
    val nowRemaining: Float,
    val oneHourRemaining: Float?,
    val todayRemaining: Float?
)

internal fun quotaPaceGuide(window: UsageWindow, now: Instant, zone: ZoneId = ZoneId.systemDefault()): QuotaPaceGuide? {
    if (!window.utilization.isFinite()) return null
    val duration = window.windowDurationSeconds?.takeIf { it in 1..366L * 86400 } ?: return null
    val reset = window.resetsAt?.takeIf { it.isAfter(now) } ?: return null
    val start = runCatching { reset.minusSeconds(duration) }.getOrNull() ?: return null
    if (now.isBefore(start)) return null
    fun remaining(at: Instant): Float? = if (at < start || !at.isBefore(reset)) null
        else ((reset.epochSecond - at.epochSecond).toDouble() / duration).toFloat().coerceIn(0f, 1f)
    val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    return QuotaPaceGuide(remaining(now) ?: return null, remaining(now.plusSeconds(3600)), remaining(midnight))
}
