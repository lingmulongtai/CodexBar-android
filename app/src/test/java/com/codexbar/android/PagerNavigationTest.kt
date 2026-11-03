package com.codexbar.android

import org.junit.Assert.*
import org.junit.Test

class PagerNavigationTest {
    @Test fun `deep links select a stable tab or settings pane without a transient dashboard`() {
        assertEquals(PageLocation(0), initialPageLocation("dashboard"))
        assertEquals(PageLocation(1), initialPageLocation("history"))
        assertEquals(PageLocation(1), initialPageLocation("spend"))
        assertEquals(PageLocation(2), initialPageLocation("connections"))
        assertEquals(PageLocation(3), initialPageLocation("github"))
        assertEquals(PageLocation(0, 1), initialPageLocation("settings"))
        assertEquals(PageLocation(0, 2), initialPageLocation("developer-settings"))
        assertEquals(PageLocation(), initialPageLocation("unknown"))
    }
    @Test fun `settings and developer Back retain the originating tab`() {
        for (tab in 0..3) {
            assertEquals(PageLocation(tab, 1), PageLocation(tab, 2).back())
            assertEquals(PageLocation(tab), PageLocation(tab, 1).back())
        }
        assertEquals(PageLocation(), PageLocation(3).back())
        assertNull(PageLocation().back())
    }

    @Test fun `Back during settings entry returns to its origin instead of exiting`() {
        for (tab in 0..3) {
            assertEquals(PageLocation(tab), backPageLocation(PageLocation(tab), PageLocation(tab, 1), true))
            assertEquals(PageLocation(tab, 1), backPageLocation(PageLocation(tab, 1), PageLocation(tab, 2), true))
        }
        assertEquals(PageLocation(), backPageLocation(PageLocation(), PageLocation(), true))
        assertEquals(PageLocation(), backPageLocation(PageLocation(), null, true))
        assertNull(backPageLocation(PageLocation(), null, false))
    }
}
