package com.codexbar.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassHeaderGeometryTest {
    @Test fun `content stays level while the header resizes`() {
        for (visible in 100..180) {
            assertEquals(132, stableHeaderContentTop(100, visible, visible + 32))
        }
    }
    @Test fun `natural font and selector height determine content inset`() {
        assertEquals(160, stableHeaderContentTop(160, 180, 180))
        assertEquals(216, stableHeaderContentTop(160, 100, 156))
    }
}
