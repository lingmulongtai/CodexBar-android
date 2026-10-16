package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.DesktopSpend
import com.codexbar.android.core.domain.model.DesktopSpendDay
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DesktopSpendCalendarTest {
    @Test fun `sparse rows preserve unknown calendar gaps across a month boundary in PC timezone`() {
        val spend = DesktopSpend(Instant.parse("2026-10-01T00:30:00Z").epochSecond, "rolling:30", "America/Los_Angeles",
            "USD", false, null, null, null, null, null, null, false, listOf(
                DesktopSpendDay("2026-08-01", 100, 3.0, true),
                DesktopSpendDay("2026-09-30", 0, 0.0, true)))
        val days = spend.calendarDays(7)
        assertEquals(7, days.size)
        assertEquals("2026-09-24", days.first().day)
        assertEquals("2026-09-30", days.last().day)
        assertNull(days.first().costUsd)
        assertEquals(0.0, days.last().costUsd!!, 0.0)
        assertEquals(0L, days.last().tokens)
    }
}
