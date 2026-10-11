package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.CodexResetCredits
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.QuotaResetKind
import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class QuotaResetTrackerTest {
    private val now = Instant.parse("2026-10-11T00:00:00Z")

    @Test fun `first observation seeds baseline without historical alerts`() {
        assertTrue(QuotaResetTracker.observe(QuotaResetState(), quota()).activities.isEmpty())
    }

    @Test fun `switching accounts clears history and seeds a new baseline`() {
        val first = quota().copy(resetTrackingAccountKey = "first")
        val baseline = QuotaResetTracker.observe(QuotaResetState(), first).state
        val withHistory = QuotaResetTracker.observe(baseline, first.copy(fetchedAt = now.plusSeconds(1),
            codexResetCredits = CodexResetCredits(3, emptyList()))).state
        assertFalse(withHistory.history.isEmpty())
        val switched = QuotaResetTracker.observe(withHistory, quota(at = now.plusSeconds(2), count = 10)
            .copy(resetTrackingAccountKey = "second"))
        assertTrue(switched.activities.isEmpty())
        assertTrue(switched.state.history.isEmpty())
        assertEquals("second", switched.state.accountKey)
    }

    @Test fun `records elapsed reset once even when new usage is nonzero`() {
        val baseline = QuotaResetTracker.observe(QuotaResetState(), quota(reset = now.plusSeconds(60))).state
        val next = quota(at = now.plusSeconds(120), reset = now.plusSeconds(18_060))
        val transition = QuotaResetTracker.observe(baseline, next)
        assertEquals(QuotaResetKind.WINDOW_RESET, transition.activities.single().kind)
        assertEquals(now.plusSeconds(60).epochSecond, transition.activities.single().boundaryEpochSeconds)
        assertTrue(QuotaResetTracker.observe(transition.state, next.copy(fetchedAt = now.plusSeconds(180))).activities.isEmpty())
    }

    @Test fun `future boundary changes and missing timestamps do not claim a reset`() {
        val baseline = QuotaResetTracker.observe(QuotaResetState(), quota()).state
        assertTrue(QuotaResetTracker.observe(baseline, quota(at = now.plusSeconds(1), reset = now.plusSeconds(2000))).activities.isEmpty())
        val missing = QuotaResetTracker.observe(baseline, quota(at = now.plusSeconds(2), reset = null)).state
        assertEquals(baseline.resetTimes, missing.resetTimes)
    }

    @Test fun `missing credit endpoint does not erase count baseline`() {
        val baseline = QuotaResetTracker.observe(QuotaResetState(), quota(count = 1)).state
        val gap = QuotaResetTracker.observe(baseline, quota(at = now.plusSeconds(1), count = null)).state
        val added = QuotaResetTracker.observe(gap, quota(at = now.plusSeconds(2), count = 3)).activities.single()
        assertEquals(QuotaResetKind.CREDITS_ADDED, added.kind)
        assertEquals(2, added.count)
        assertEquals(3, added.availableCount)
    }

    @Test fun `count decrease is recorded without claiming redemption`() {
        val baseline = QuotaResetTracker.observe(QuotaResetState(), quota(count = 3)).state
        val removed = QuotaResetTracker.observe(baseline, quota(at = now.plusSeconds(1), count = 1)).activities.single()
        assertEquals(QuotaResetKind.CREDITS_DECREASED, removed.kind)
        assertEquals(2, removed.count)
    }

    @Test fun `out of order and duplicate observations cannot regress state`() {
        val baseline = QuotaResetTracker.observe(QuotaResetState(), quota(count = 2)).state
        for (at in listOf(now.minusSeconds(1), now)) {
            val result = QuotaResetTracker.observe(baseline, quota(at = at, count = 0))
            assertEquals(baseline, result.state)
            assertTrue(result.activities.isEmpty())
        }
    }

    @Test fun `expiry reminder is bounded and deduplicated across missing snapshots`() {
        val expiry = now.plusSeconds(3600)
        val first = quota(count = 1).copy(codexResetCredits = CodexResetCredits(1, listOf(expiry)))
        val baseline = QuotaResetTracker.observe(QuotaResetState(), first).state
        val reminder = QuotaResetTracker.observe(baseline, first.copy(fetchedAt = now.plusSeconds(1)))
        assertEquals(QuotaResetKind.CREDITS_EXPIRING, reminder.activities.single().kind)
        val gap = QuotaResetTracker.observe(reminder.state, quota(at = now.plusSeconds(2), count = null)).state
        assertTrue(QuotaResetTracker.observe(gap, first.copy(fetchedAt = now.plusSeconds(3))).activities.isEmpty())
    }

    @Test fun `history retains at most 100 events and 90 days`() {
        var state = QuotaResetState()
        repeat(150) { state = QuotaResetTracker.observe(state, quota(at = now.plusSeconds(it.toLong()), count = it)).state }
        assertEquals(100, state.history.size)
        val later = now.plusMillis(QuotaResetTracker.RETENTION_MILLIS + 200_000)
        assertTrue(QuotaResetTracker.observe(state, quota(at = later, count = 149)).state.history.isEmpty())
    }

    private fun quota(at: Instant = now, reset: Instant? = now.plusSeconds(1000), count: Int? = 1) = QuotaInfo(
        service = AiService.CODEX,
        windows = listOf(UsageWindow("5-Hour", 0.2, reset, 18_000)),
        extraUsage = null,
        fetchedAt = at,
        codexResetCredits = count?.let { CodexResetCredits(it, emptyList()) }
    )
}
