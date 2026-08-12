package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

data class QuotaResetPlan(
    val resetAt: Instant,
    val minutesUntilReset: Long,
    val remainingPercent: Int,
    val sustainablePercentPerHour: Double,
    val sustainablePercentPerDay: Double,
    val checkpointAt: Instant,
    val checkpointBudgetPercent: Int,
    val action: ResetPlanAction
)

enum class ResetPlanAction {
    AlmostUsed,
    UseNow,
    SlowDown,
    KeepPace,
    UseByCheckpoint
}

class QuotaResetPlanCalculator {
    fun calculate(
        window: UsageWindow,
        paceState: PaceState,
        now: Instant
    ): QuotaResetPlan? {
        val utilization = window.utilization
            .takeIf(Double::isFinite)
            ?.coerceIn(0.0, 1.0)
            ?: return null
        val resetAt = window.resetsAt?.takeIf { it.isAfter(now) } ?: return null
        val secondsUntilReset = Duration.between(now, resetAt).seconds
            .takeIf { it > 0L }
            ?: return null
        val remainingFraction = (1.0 - utilization).coerceIn(0.0, 1.0)
        val remainingPercent = (remainingFraction * 100.0).roundToInt().coerceIn(0, 100)
        val hoursUntilReset = secondsUntilReset / SECONDS_PER_HOUR
        val checkpointSeconds = checkpointSeconds(secondsUntilReset)
        val checkpointAt = now.plusSeconds(checkpointSeconds)
        val checkpointBudgetPercent = if (remainingPercent == 0) {
            0
        } else {
            ceil(remainingPercent * checkpointSeconds.toDouble() / secondsUntilReset.toDouble())
                .toInt()
                .coerceIn(1, remainingPercent)
        }
        val action = when {
            remainingPercent <= ALMOST_USED_PERCENT -> ResetPlanAction.AlmostUsed
            secondsUntilReset <= USE_NOW_THRESHOLD_SECONDS -> ResetPlanAction.UseNow
            paceState == PaceState.Exhausting || paceState == PaceState.AtRisk -> {
                ResetPlanAction.SlowDown
            }
            paceState == PaceState.OnTrack -> ResetPlanAction.KeepPace
            else -> ResetPlanAction.UseByCheckpoint
        }

        return QuotaResetPlan(
            resetAt = resetAt,
            minutesUntilReset = ceil(secondsUntilReset / SECONDS_PER_MINUTE)
                .toLong()
                .coerceAtLeast(1L),
            remainingPercent = remainingPercent,
            sustainablePercentPerHour = remainingFraction * 100.0 / hoursUntilReset,
            sustainablePercentPerDay = remainingFraction * 100.0 * HOURS_PER_DAY /
                hoursUntilReset,
            checkpointAt = checkpointAt,
            checkpointBudgetPercent = checkpointBudgetPercent,
            action = action
        )
    }

    private fun checkpointSeconds(secondsUntilReset: Long): Long {
        val interval = when {
            secondsUntilReset >= TWO_DAYS_SECONDS -> ONE_DAY_SECONDS
            secondsUntilReset >= TWELVE_HOURS_SECONDS -> SIX_HOURS_SECONDS
            secondsUntilReset >= FOUR_HOURS_SECONDS -> TWO_HOURS_SECONDS
            secondsUntilReset >= TWO_HOURS_SECONDS -> ONE_HOUR_SECONDS
            else -> secondsUntilReset
        }
        return interval.coerceAtMost(secondsUntilReset)
    }

    private companion object {
        const val ALMOST_USED_PERCENT = 2
        const val SECONDS_PER_MINUTE = 60.0
        const val SECONDS_PER_HOUR = 3600.0
        const val HOURS_PER_DAY = 24.0
        const val ONE_HOUR_SECONDS = 60L * 60L
        const val TWO_HOURS_SECONDS = 2L * ONE_HOUR_SECONDS
        const val FOUR_HOURS_SECONDS = 4L * ONE_HOUR_SECONDS
        const val SIX_HOURS_SECONDS = 6L * ONE_HOUR_SECONDS
        const val TWELVE_HOURS_SECONDS = 12L * ONE_HOUR_SECONDS
        const val ONE_DAY_SECONDS = 24L * ONE_HOUR_SECONDS
        const val TWO_DAYS_SECONDS = 2L * ONE_DAY_SECONDS
        const val USE_NOW_THRESHOLD_SECONDS = TWO_HOURS_SECONDS
    }
}
