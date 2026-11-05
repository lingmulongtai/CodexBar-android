package com.codexbar.android.feature.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardAdaptiveLayoutTest {
    @Test
    fun `medium windows retain the full provider overview`() {
        assertFalse(useTwoPaneDashboard(720f, 1280f))
        assertFalse(useTwoPaneDashboard(840f, 720f))
        assertFalse(useTwoPaneDashboard(1119f, 800f))
        assertTrue(useTwoPaneDashboard(1120f, 800f))
    }

    @Test
    fun `wide but shallow windows and large text do not squeeze details`() {
        assertFalse(useTwoPaneDashboard(1440f, 599f))
        assertTrue(useTwoPaneDashboard(1440f, 600f))
        assertFalse(useTwoPaneDashboard(1280f, 900f, 1.3f))
        assertTrue(useTwoPaneDashboard(1440f, 900f, 1.3f))
        assertFalse(useTwoPaneDashboard(1440f, 700f, 1.3f))
    }
}
