package com.codexbar.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassControlGeometryTest {
    @Test fun `tabs follow centres and clamp a drag beyond either end`() {
        assertEquals(0f, glassTabPosition(50f, 300f, 3, false), 0.001f)
        assertEquals(1f, glassTabPosition(150f, 300f, 3, false), 0.001f)
        assertEquals(1.5f, glassTabPosition(200f, 300f, 3, false), 0.001f)
        assertEquals(0f, glassTabPosition(-500f, 300f, 3, false), 0.001f)
        assertEquals(2f, glassTabPosition(900f, 300f, 3, false), 0.001f)
    }

    @Test fun `RTL tabs reverse the physical order without reversing logical indices`() {
        assertEquals(2f, glassTabPosition(50f, 300f, 3, true), 0.001f)
        assertEquals(0f, glassTabPosition(250f, 300f, 3, true), 0.001f)
        assertEquals(0f, glassTabPosition(0f, 0f, 0, false), 0.001f)
    }

    @Test fun `a switch keeps its origin and crosses the midpoint only after enough travel`() {
        assertEquals(0f, glassSwitchPosition(0f, 0f, 28f, false), 0.001f)
        assertEquals(0.25f, glassSwitchPosition(0f, 7f, 28f, false), 0.001f)
        assertEquals(1f, glassSwitchPosition(0f, 80f, 28f, false), 0.001f)
        assertEquals(0f, glassSwitchPosition(1f, -80f, 28f, false), 0.001f)
        assertEquals(0.25f, glassSwitchPosition(1f, 21f, 28f, true), 0.001f)
        assertEquals(1f, glassSwitchPosition(1f, 80f, 0f, false), 0.001f)
    }
}
