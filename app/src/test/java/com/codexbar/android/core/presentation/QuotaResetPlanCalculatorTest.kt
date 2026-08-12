package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaResetPlanCalculatorTest {
    private val calculator = QuotaResetPlanCalculator()
    private val now = Instant.parse("2026-08-12T12:00:00Z")

    @Test
    fun `builds a balanced daily checkpoint for a long Codex window`() {
        val plan = calculator.calculate(
            window = window(
                utilization = 0.40,
                resetAt = now.plusSeconds(72 * 60 * 60L)
            ),
            paceState = PaceState.OnTrack,
            now = now
        )!!

        assertEquals(60, plan.remainingPercent)
        assertEquals(72 * 60L, plan.minutesUntilReset)
        assertEquals(20.0 / 24.0, plan.sustainablePercentPerHour, 0.001)
        assertEquals(20.0, plan.sustainablePercentPerDay, 0.001)
        assertEquals(now.plusSeconds(24 * 60 * 60L), plan.checkpointAt)
        assertEquals(20, plan.checkpointBudgetPercent)
        assertEquals(ResetPlanAction.KeepPace, plan.action)
    }

    @Test
    fun `recommends using available quota now when reset is close`() {
        val resetAt = now.plusSeconds(90 * 60L)
        val plan = calculator.calculate(
            window = window(utilization = 0.70, resetAt = resetAt),
            paceState = PaceState.CollectingHistory,
            now = now
        )!!

        assertEquals(30, plan.remainingPercent)
        assertEquals(90, plan.minutesUntilReset)
        assertEquals(resetAt, plan.checkpointAt)
        assertEquals(30, plan.checkpointBudgetPercent)
        assertEquals(ResetPlanAction.UseNow, plan.action)
    }

    @Test
    fun `turns an at-risk pace into a conservative checkpoint`() {
        val plan = calculator.calculate(
            window = window(
                utilization = 0.80,
                resetAt = now.plusSeconds(24 * 60 * 60L)
            ),
            paceState = PaceState.AtRisk,
            now = now
        )!!

        assertEquals(now.plusSeconds(6 * 60 * 60L), plan.checkpointAt)
        assertEquals(5, plan.checkpointBudgetPercent)
        assertEquals(ResetPlanAction.SlowDown, plan.action)
    }

    @Test
    fun `marks a nearly exhausted window before other advice`() {
        val plan = calculator.calculate(
            window = window(
                utilization = 0.99,
                resetAt = now.plusSeconds(30 * 60L)
            ),
            paceState = PaceState.Exhausting,
            now = now
        )!!

        assertEquals(1, plan.remainingPercent)
        assertEquals(ResetPlanAction.AlmostUsed, plan.action)
    }

    @Test
    fun `returns no plan for unavailable or expired reset data`() {
        assertNull(
            calculator.calculate(
                window = window(Double.NaN, now.plusSeconds(3600)),
                paceState = PaceState.Unknown,
                now = now
            )
        )
        assertNull(
            calculator.calculate(
                window = window(0.5, null),
                paceState = PaceState.Unknown,
                now = now
            )
        )
        assertNull(
            calculator.calculate(
                window = window(0.5, now),
                paceState = PaceState.Unknown,
                now = now
            )
        )
    }

    private fun window(utilization: Double, resetAt: Instant?): UsageWindow {
        return UsageWindow(
            label = "7-Day",
            utilization = utilization,
            resetsAt = resetAt,
            windowDurationSeconds = 7 * 24 * 60 * 60L
        )
    }
}
