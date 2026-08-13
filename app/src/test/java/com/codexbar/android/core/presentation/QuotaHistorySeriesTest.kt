package com.codexbar.android.core.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class QuotaHistorySeriesTest {
    private val now = Instant.parse("2026-08-13T00:00:00Z")

    @Test
    fun `keeps recent detail and compacts older history hourly`() {
        val samples = (0 until 8).map { index ->
            QuotaHistorySample(
                fetchedAt = now.minusSeconds(48 * 3600L).plusSeconds(index * 15 * 60L),
                utilization = index / 10.0,
                resetsAt = now.plusSeconds(3600)
            )
        } + (0 until 8).map { index ->
            QuotaHistorySample(
                fetchedAt = now.minusSeconds(2 * 3600L).plusSeconds(index * 15 * 60L),
                utilization = index / 10.0,
                resetsAt = now.plusSeconds(3600)
            )
        }

        val compacted = QuotaHistorySeries.compact(samples, now)

        assertEquals(10, compacted.size)
        assertEquals(8, compacted.count { it.fetchedAt >= now.minusSeconds(24 * 3600L) })
    }

    @Test
    fun `chart sampling preserves both sides of a reset`() {
        val oldReset = now.plusSeconds(3600)
        val newReset = now.plusSeconds(8 * 24 * 3600L)
        val samples = (0 until 200).map { index ->
            QuotaHistorySample(
                fetchedAt = now.minusSeconds((200 - index) * 60L),
                utilization = if (index < 100) index / 100.0 else (index - 100) / 100.0,
                resetsAt = if (index < 100) oldReset else newReset
            )
        }

        val chart = QuotaHistorySeries.forChart(samples, maxPoints = 24)

        assertTrue(chart.size <= 24)
        assertTrue(chart.any { it.fetchedAt == samples[99].fetchedAt })
        assertTrue(chart.any { it.fetchedAt == samples[100].fetchedAt })
        assertEquals(samples.first(), chart.first())
        assertEquals(samples.last(), chart.last())
    }

    @Test
    fun `drops stale invalid and far-future samples`() {
        val valid = QuotaHistorySample(now.minusSeconds(60), 0.4, null)
        val compacted = QuotaHistorySeries.compact(
            listOf(
                valid,
                QuotaHistorySample(now.minusSeconds(91 * 24 * 3600L), 0.2, null),
                QuotaHistorySample(now.plusSeconds(600), 0.3, null),
                QuotaHistorySample(now.minusSeconds(30), Double.NaN, null)
            ),
            now
        )

        assertEquals(listOf(valid), compacted)
    }
}
