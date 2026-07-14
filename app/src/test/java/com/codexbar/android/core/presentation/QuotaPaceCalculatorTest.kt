package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class QuotaPaceCalculatorTest {
    private val calculator = QuotaPaceCalculator()
    private val now = Instant.parse("2026-07-13T12:00:00Z")
    private val resetAt = Instant.parse("2026-07-14T12:00:00Z")

    @Test
    fun `calculates pace from the full reset cycle without waiting for history`() {
        val pace = calculator.calculate(
            samples = emptyList(),
            currentWindow = UsageWindow(
                label = "48h",
                utilization = 0.40,
                resetsAt = resetAt,
                windowDurationSeconds = 48 * 60 * 60L
            ),
            now = now
        )

        assertEquals(PaceState.OnTrack, pace.state)
        assertEquals("You're on a good pace", pace.label)
        assertEquals("Used 40% / 50.0% of window elapsed", pace.cycleProgressLabel)
        assertEquals("Avg 1.7%/h", pace.usageRateLabel)
        assertEquals("0.80× target pace", pace.paceMultiplierLabel)
        assertEquals("20% reserve", pace.reserveLabel)
        assertEquals("At this pace: 80% by reset", pace.forecastLabel)
    }

    @Test
    fun `flags an overusing pace against the current reset cycle`() {
        val pace = calculator.calculate(
            samples = emptyList(),
            currentWindow = UsageWindow(
                label = "5-Hour",
                utilization = 0.70,
                resetsAt = now.plusSeconds(3 * 60 * 60L),
                windowDurationSeconds = 5 * 60 * 60L
            ),
            now = now
        )

        assertEquals(PaceState.Exhausting, pace.state)
        assertEquals("Usage pace is too fast", pace.label)
        assertEquals("Used 70% / 40.0% of window elapsed", pace.cycleProgressLabel)
        assertEquals("Avg 35.0%/h", pace.usageRateLabel)
        assertEquals("1.75× target pace", pace.paceMultiplierLabel)
        assertEquals("0% reserve", pace.reserveLabel)
        assertEquals("At this pace: 175% by reset", pace.forecastLabel)
    }

    @Test
    fun `collects history when duration and comparable samples are unavailable`() {
        val pace = calculator.calculate(
            samples = emptyList(),
            currentWindow = UsageWindow("weekly", 0.4, resetAt),
            now = now
        )

        assertEquals(PaceState.CollectingHistory, pace.state)
        assertEquals("Collecting pace history", pace.label)
        assertNull(pace.usageRateLabel)
    }

    @Test
    fun `falls back to observed pace when provider omits window duration`() {
        val pace = calculator.calculate(
            samples = listOf(
                QuotaHistorySample(
                    fetchedAt = now.minusSeconds(6 * 3600),
                    utilization = 0.10,
                    resetsAt = resetAt
                )
            ),
            currentWindow = UsageWindow("weekly", 0.22, resetAt),
            now = now
        )

        assertEquals(PaceState.OnTrack, pace.state)
        assertEquals("You're on a good pace", pace.label)
        assertNull(pace.cycleProgressLabel)
        assertEquals("Avg 2.0%/h", pace.usageRateLabel)
        assertEquals("0.62× target pace", pace.paceMultiplierLabel)
        assertEquals("30% reserve", pace.reserveLabel)
        assertEquals("At this pace: 70% by reset", pace.forecastLabel)
    }

    @Test
    fun `does not connect samples across reset windows`() {
        val pace = calculator.calculate(
            samples = listOf(
                QuotaHistorySample(
                    fetchedAt = now.minusSeconds(6 * 3600),
                    utilization = 0.95,
                    resetsAt = resetAt.minusSeconds(86400)
                )
            ),
            currentWindow = UsageWindow("weekly", 0.05, resetAt),
            now = now
        )

        assertEquals(PaceState.CollectingHistory, pace.state)
    }
}
