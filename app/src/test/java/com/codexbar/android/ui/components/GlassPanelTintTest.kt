package com.codexbar.android.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPanelTintTest {
    @Test fun `subtle status tints remain pale rather than becoming saturated backgrounds`() {
        val mint = glassPanelTint(Color(0f, 0.5f, 0.4f, 0.08f), Color.White, false)
        assertEquals(0.92f, mint.red, 0.01f)
        assertEquals(0.96f, mint.green, 0.01f)
        assertTrue(mint.blue > 0.94f)
        assertEquals(0.72f, mint.alpha, 0.01f)
    }

    @Test fun `a transparent panel retains its neutral plate in both appearances`() {
        assertTrue(glassPanelTint(Color.Transparent, Color.White, false).red > 0.99f)
        val dark = glassPanelTint(Color.Transparent, Color(0xFF1C1C1E), true)
        assertEquals(28f / 255f, dark.red, 0.01f)
        assertEquals(0.74f, dark.alpha, 0.01f)
    }
}
