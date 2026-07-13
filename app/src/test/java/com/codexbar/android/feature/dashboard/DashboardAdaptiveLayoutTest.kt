package com.codexbar.android.feature.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardAdaptiveLayoutTest {
    @Test
    fun `dashboard uses two panes only when content has enough width`() {
        assertFalse(useTwoPaneDashboard(719f))
        assertTrue(useTwoPaneDashboard(720f))
        assertTrue(useTwoPaneDashboard(1000f))
    }
}
