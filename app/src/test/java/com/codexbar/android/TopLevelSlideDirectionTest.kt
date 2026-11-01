package com.codexbar.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelSlideDirectionTest {
    @Test fun `tab order and RTL determine slide direction`() {
        assertEquals(1, topLevelSlideDirection("dashboard", "history", false))
        assertEquals(-1, topLevelSlideDirection("github", "dashboard", false))
        assertEquals(-1, topLevelSlideDirection("dashboard", "connections", true))
        assertEquals(1, topLevelSlideDirection("connections", "history", true))
        assertEquals(0, topLevelSlideDirection("history", "spend", false))
        assertEquals(0, topLevelSlideDirection("unknown", "dashboard", false))
    }
}
