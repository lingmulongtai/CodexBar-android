package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

data class QuotaHistorySample(
    val fetchedAt: Instant,
    val utilization: Double,
    val resetsAt: Instant?
)

class QuotaPaceCalculator(
    private val text: QuotaPresentationText = EnglishQuotaPresentationText
) {
    fun calculate(
        samples: List<QuotaHistorySample>,
        currentWindow: UsageWindow,
        now: Instant
    ): PacePresentation {
        val currentUsed = currentWindow.utilization.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
            ?: return unknown()
        val resetAt = currentWindow.resetsAt ?: return collecting(text.noResetTime())
        if (!resetAt.isAfter(now)) return collecting(text.waitingForNextWindow())

        currentWindow.windowDurationSeconds
            ?.takeIf { it > 0L }
            ?.let { durationSeconds ->
                return calculateFromCycle(
                    currentUsed = currentUsed,
                    resetAt = resetAt,
                    durationSeconds = durationSeconds,
                    now = now
                )
            }

        return calculateFromHistory(
            samples = samples,
            currentUsed = currentUsed,
            resetAt = resetAt,
            now = now
        )
    }

    private fun calculateFromCycle(
        currentUsed: Double,
        resetAt: Instant,
        durationSeconds: Long,
        now: Instant
    ): PacePresentation {
        val cycleStartedAt = resetAt.minusSeconds(durationSeconds)
        val elapsedSeconds = Duration.between(cycleStartedAt, now).seconds
        if (elapsedSeconds < MIN_SAMPLE_SPACING_SECONDS || elapsedSeconds >= durationSeconds) {
            return collecting(text.collectingPaceHistory())
        }

        val elapsedHours = elapsedSeconds / SECONDS_PER_HOUR
        val elapsedFraction = elapsedSeconds.toDouble() / durationSeconds.toDouble()
        val usagePerHour = currentUsed / elapsedHours
        val projectedUsed = currentUsed / elapsedFraction

        return present(
            usagePerHour = usagePerHour,
            projectedUsed = projectedUsed,
            paceMultiplier = projectedUsed,
            cycleProgressLabel = text.cycleProgress(
                usedPercent = currentUsed.toPercent(),
                elapsedPercent = formatDecimal(elapsedFraction * 100.0, 1)
            )
        )
    }

    private fun calculateFromHistory(
        samples: List<QuotaHistorySample>,
        currentUsed: Double,
        resetAt: Instant,
        now: Instant
    ): PacePresentation {
        val comparableSamples = samples
            .filter { sample ->
                sample.fetchedAt.isBefore(now) &&
                    sample.resetsAt == resetAt &&
                    sample.utilization.isFinite()
            }
            .sortedBy { it.fetchedAt }

        val baseline = comparableSamples.firstOrNull {
            it.fetchedAt.isBefore(now.minusSeconds(MIN_SAMPLE_SPACING_SECONDS))
        } ?: return collecting(text.collectingPaceHistory())

        val elapsedHours = Duration.between(baseline.fetchedAt, now).toMinutes() / 60.0
        if (elapsedHours <= 0.0) return collecting(text.collectingPaceHistory())

        val usedDelta = (currentUsed - baseline.utilization.coerceIn(0.0, 1.0)).coerceAtLeast(0.0)
        val usagePerHour = usedDelta / elapsedHours
        val hoursUntilReset = Duration.between(now, resetAt).toMinutes() / 60.0
        val projectedUsed = (currentUsed + usagePerHour * hoursUntilReset).coerceAtLeast(0.0)
        val sustainableUsagePerHour = if (hoursUntilReset > 0.0) {
            (1.0 - currentUsed).coerceAtLeast(0.0) / hoursUntilReset
        } else {
            0.0
        }
        val paceMultiplier = when {
            usagePerHour <= 0.0 -> 0.0
            sustainableUsagePerHour > 0.0 -> usagePerHour / sustainableUsagePerHour
            else -> null
        }

        return present(
            usagePerHour = usagePerHour,
            projectedUsed = projectedUsed,
            paceMultiplier = paceMultiplier,
            cycleProgressLabel = null
        )
    }

    private fun present(
        usagePerHour: Double,
        projectedUsed: Double,
        paceMultiplier: Double?,
        cycleProgressLabel: String?
    ): PacePresentation {
        val reservePercent = ((1.0 - projectedUsed).coerceAtLeast(0.0) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        val projectedPercent = (projectedUsed * 100.0)
            .roundToInt()
            .coerceIn(0, MAX_DISPLAYED_FORECAST_PERCENT)

        val state = when {
            projectedUsed >= EXHAUSTING_FORECAST_FRACTION -> PaceState.Exhausting
            projectedUsed >= AT_RISK_FORECAST_FRACTION -> PaceState.AtRisk
            else -> PaceState.OnTrack
        }
        val label = when {
            projectedUsed >= OVERUSING_FORECAST_FRACTION -> text.paceTooFast()
            projectedUsed >= EXHAUSTING_FORECAST_FRACTION -> text.mayRunOutBeforeReset()
            projectedUsed >= AT_RISK_FORECAST_FRACTION -> text.paceAtRisk()
            projectedUsed >= GOOD_PACE_FORECAST_FRACTION -> text.paceOnTrack()
            else -> text.roomToUseMore()
        }

        return PacePresentation(
            state = state,
            label = label,
            cycleProgressLabel = cycleProgressLabel,
            usageRateLabel = text.usageRate(
                formatDecimal(usagePerHour.coerceAtLeast(0.0) * 100.0, 1)
            ),
            paceMultiplierLabel = paceMultiplier?.takeIf { it.isFinite() }?.let {
                text.paceMultiplier(formatDecimal(it.coerceIn(0.0, MAX_DISPLAYED_MULTIPLIER), 2))
            },
            reserveLabel = text.reserve(reservePercent),
            forecastLabel = text.forecastAtReset(projectedPercent)
        )
    }

    private fun formatDecimal(value: Double, decimals: Int): String {
        return String.format(text.locale, "%.${decimals}f", value)
    }

    private fun Double.toPercent(): Int = (this * 100.0).roundToInt().coerceIn(0, 100)

    private fun collecting(reason: String): PacePresentation {
        return PacePresentation(
            state = PaceState.CollectingHistory,
            label = reason
        )
    }

    private fun unknown(): PacePresentation {
        return PacePresentation(
            state = PaceState.Unknown,
            label = text.paceUnavailable()
        )
    }

    private companion object {
        const val MIN_SAMPLE_SPACING_SECONDS = 60L
        const val SECONDS_PER_HOUR = 3600.0
        const val GOOD_PACE_FORECAST_FRACTION = 0.65
        const val AT_RISK_FORECAST_FRACTION = 0.90
        const val EXHAUSTING_FORECAST_FRACTION = 1.0
        const val OVERUSING_FORECAST_FRACTION = 1.15
        const val MAX_DISPLAYED_FORECAST_PERCENT = 999
        const val MAX_DISPLAYED_MULTIPLIER = 99.99
    }
}
