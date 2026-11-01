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
        // The round thumb travels 22 dp in the 50 dp track.
        assertEquals(0.5f, glassSwitchPosition(0f, 11f, 22f, false), 0.001f)
        assertEquals(0.5f, glassSwitchPosition(1f, 11f, 22f, true), 0.001f)
    }
    @Test fun `slider reports five-minute values without moving the input position`() {
        assertEquals(15f, glassSliderValue(15f, 15f..180f, 32), 0.001f)
        assertEquals(20f, glassSliderValue(18f, 15f..180f, 32), 0.001f)
        assertEquals(75f, glassSliderValue(72.6f, 15f..180f, 32), 0.001f)
        assertEquals(180f, glassSliderValue(200f, 15f..180f, 32), 0.001f)
        assertEquals(37.25f, glassSliderValue(37.25f, 0f..100f, 0), 0.001f)
    }

}
