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

        val comparableSamples = samples
            .filter { sample ->
                sample.fetchedAt.isBefore(now) &&
                    sample.resetsAt == resetAt &&
                    sample.utilization.isFinite()
            }
            .sortedBy { it.fetchedAt }

        val baseline = comparableSamples.firstOrNull { it.fetchedAt.isBefore(now.minusSeconds(MIN_SAMPLE_SPACING_SECONDS)) }
            ?: return collecting(text.collectingPaceHistory())

        val elapsedHours = Duration.between(baseline.fetchedAt, now).toMinutes() / 60.0
        if (elapsedHours <= 0.0) return collecting(text.collectingPaceHistory())

        val usedDelta = (currentUsed - baseline.utilization.coerceIn(0.0, 1.0)).coerceAtLeast(0.0)
        if (usedDelta <= 0.0) {
            return PacePresentation(
                state = PaceState.OnTrack,
                label = text.paceStable(),
                reserveLabel = text.reserve(
                    ((1.0 - currentUsed) * 100).roundToInt().coerceIn(0, 100)
                ),
                forecastLabel = text.noRecentIncrease()
            )
        }

        val usagePerHour = usedDelta / elapsedHours
        val hoursUntilReset = Duration.between(now, resetAt).toMinutes() / 60.0
        val projectedUsed = (currentUsed + usagePerHour * hoursUntilReset).coerceAtLeast(0.0)
        val reserve = (1.0 - projectedUsed).coerceAtLeast(0.0)
        val projectedPercent = (projectedUsed * 100).roundToInt().coerceAtLeast(0)
        val reservePercent = (reserve * 100).roundToInt().coerceIn(0, 100)

        val state = when {
            projectedUsed >= 1.0 -> PaceState.Exhausting
            projectedUsed >= 0.85 -> PaceState.AtRisk
            else -> PaceState.OnTrack
        }
        val label = when (state) {
            PaceState.Exhausting -> text.mayRunOutBeforeReset()
            PaceState.AtRisk -> text.paceAtRisk()
            PaceState.OnTrack -> text.paceOnTrack()
            PaceState.Unknown,
            PaceState.CollectingHistory -> text.collectingPaceHistory()
        }
        return PacePresentation(
            state = state,
            label = label,
            reserveLabel = text.reserve(reservePercent),
            forecastLabel = text.forecastAtReset(projectedPercent)
        )
    }

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
    }
}
