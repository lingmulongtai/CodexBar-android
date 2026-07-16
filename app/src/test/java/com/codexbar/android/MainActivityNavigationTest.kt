package com.codexbar.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityNavigationTest {
    @Test
    fun `settings deep link opens settings`() {
        assertEquals("settings", startDestinationForHost("settings"))
        assertEquals("settings", startDestinationForHost("gemini-pair"))
    }

    @Test
    fun `other launches open dashboard`() {
        assertEquals("dashboard", startDestinationForHost(null))
        assertEquals("dashboard", startDestinationForHost("dashboard"))
        assertEquals("dashboard", startDestinationForHost("unexpected"))
    }

    @Test
    fun `navigation moves to rail at the medium width breakpoint`() {
        assertFalse(useExpandedNavigation(599f))
        assertTrue(useExpandedNavigation(600f))
        assertTrue(useExpandedNavigation(840f))
    }
}
