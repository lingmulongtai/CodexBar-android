package com.codexbar.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopLevelSwipeTest {
    @Test fun `horizontal gestures move one page and respect RTL and edges`() {
        assertEquals("history", swipeTarget("dashboard", -100f, 0f, 60f, false))
        assertEquals("github", swipeTarget("connections", -100f, 0f, 60f, false))
        assertEquals("connections", swipeTarget("github", 100f, 0f, 60f, false))
        assertEquals("history", swipeTarget("dashboard", 100f, 0f, 60f, true))
        assertNull(swipeTarget("dashboard", 100f, 0f, 60f, false))
        assertNull(swipeTarget("github", -100f, 0f, 60f, false))
    }
    @Test fun `short diagonal and non-tab gestures do not navigate`() {
        assertNull(swipeTarget("dashboard", -40f, 0f, 60f, false))
        assertNull(swipeTarget("dashboard", -100f, 90f, 60f, false))
        assertNull(swipeTarget("settings", -100f, 0f, 60f, false))
        assertEquals("connections", swipeTarget("spend", -100f, 0f, 60f, false))
    }
}
