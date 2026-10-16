package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class QuotaPaceGuideTest {
    private val now = Instant.parse("2026-10-17T12:00:00Z")
    @Test fun `guides use remaining direction and disappear beyond reset`() {
        val window = UsageWindow("Current", 0.2, now.plusSeconds(7200), 18000)
        val guide = quotaPaceGuide(window, now, ZoneId.of("UTC"))!!
        assertEquals(0.4f, guide.nowRemaining, 0.0001f)
        assertEquals(0.2f, guide.oneHourRemaining!!, 0.0001f)
        assertNull(guide.todayRemaining)
        assertNull(quotaPaceGuide(window.copy(resetsAt = now), now))
    }
    @Test fun `unknown duration or invalid cycle cannot invent a guide`() {
        assertNull(quotaPaceGuide(UsageWindow("Unknown", 0.0, now.plusSeconds(7200)), now))
        assertNull(quotaPaceGuide(UsageWindow("Future", 0.0, now.plusSeconds(7200), 3600), now))
        assertNull(quotaPaceGuide(UsageWindow("Invalid", Double.NaN, now.plusSeconds(7200), 18000), now))
    }
    @Test fun `today uses the PC or device civil midnight including DST`() {
        val guide = quotaPaceGuide(UsageWindow("Week", 0.0, now.plusSeconds(86400 * 3), 86400 * 7), now, ZoneId.of("Asia/Tokyo"))!!
        assertTrue(guide.todayRemaining!! < guide.nowRemaining)
        assertEquals(guide.nowRemaining - 3f / (24 * 7), guide.todayRemaining!!, 0.0001f)
    }
}
