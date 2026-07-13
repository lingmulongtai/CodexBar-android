package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class QuotaPaceCalculatorTest {
    private val calculator = QuotaPaceCalculator()
    private val now = Instant.parse("2026-07-13T12:00:00Z")
    private val resetAt = Instant.parse("2026-07-14T12:00:00Z")

    @Test
    fun `collects history until comparable samples exist`() {
        val pace = calculator.calculate(
            samples = emptyList(),
            currentWindow = UsageWindow("weekly", 0.4, resetAt),
            now = now
        )

        assertEquals(PaceState.CollectingHistory, pace.state)
        assertEquals("Collecting pace history", pace.label)
    }

    @Test
    fun `projects on-track reserve from same reset window`() {
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
        assertEquals("Pace on track", pace.label)
        assertEquals("30% reserve", pace.reserveLabel)
        assertEquals("At this pace: 70% by reset", pace.forecastLabel)
    }

    @Test
    fun `flags exhausting pace before reset`() {
        val pace = calculator.calculate(
            samples = listOf(
                QuotaHistorySample(
                    fetchedAt = now.minusSeconds(2 * 3600),
                    utilization = 0.20,
                    resetsAt = resetAt
                )
            ),
            currentWindow = UsageWindow("5h", 0.70, resetAt),
            now = now
        )

        assertEquals(PaceState.Exhausting, pace.state)
        assertEquals("May run out before reset", pace.label)
        assertEquals("0% reserve", pace.reserveLabel)
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
