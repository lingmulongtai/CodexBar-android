package com.codexbar.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityNavigationTest {
    @Test
    fun `settings deep link opens settings`() {
        assertEquals("settings", startDestinationForHost("settings"))
    }

    @Test
    fun `other launches open dashboard`() {
        assertEquals("dashboard", startDestinationForHost(null))
        assertEquals("dashboard", startDestinationForHost("dashboard"))
        assertEquals("dashboard", startDestinationForHost("unexpected"))
    }
}
