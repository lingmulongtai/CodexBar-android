package com.codexbar.android.core.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringSessionTest {

    @Test
    fun `session is active only between explicit start and end`() {
        val session = MonitoringSession(
            startedAtMillis = 1_000,
            endsAtMillis = 61_000,
            cadenceMinutes = 15
        )

        assertFalse(session.isActive(999))
        assertTrue(session.isActive(1_000))
        assertTrue(session.isActive(60_999))
        assertFalse(session.isActive(61_000))
    }

    @Test
    fun `remaining minutes rounds up without going negative`() {
        val session = MonitoringSession(
            startedAtMillis = 0,
            endsAtMillis = 120_000,
            cadenceMinutes = 15
        )

        assertEquals(2, session.remainingMinutes(1))
        assertEquals(1, session.remainingMinutes(60_001))
        assertEquals(0, session.remainingMinutes(120_000))
        assertEquals(0, session.remainingMinutes(121_000))
    }

    @Test
    fun `monitoring duration stays within supported session bounds`() {
        assertEquals(15, normalizeMonitoringDuration(1))
        assertEquals(60, normalizeMonitoringDuration(60))
        assertEquals(180, normalizeMonitoringDuration(999))
    }
}
