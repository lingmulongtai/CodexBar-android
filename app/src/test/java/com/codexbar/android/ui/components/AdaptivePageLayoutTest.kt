package com.codexbar.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePageLayoutTest {
    @Test fun `provider columns include both side insets and every gap`() {
        assertEquals(1, adaptiveCardColumns(320f, 340f))
        assertEquals(1, adaptiveCardColumns(712f, 340f))
        assertEquals(1, adaptiveCardColumns(723f, 340f))
        assertEquals(2, adaptiveCardColumns(724f, 340f))
        assertEquals(2, adaptiveCardColumns(1075f, 340f))
        assertEquals(3, adaptiveCardColumns(1076f, 340f))
        assertEquals(3, adaptiveCardColumns(2000f, 340f))
    }

    @Test fun `form and chart grids retain readable widths`() {
        assertEquals(1, adaptiveCardColumns(967f, 460f, gapDp = 16f, maximum = 2))
        assertEquals(2, adaptiveCardColumns(968f, 460f, gapDp = 16f, maximum = 2))
        assertEquals(1, adaptiveCardColumns(724f, 340f, gapDp = 16f))
        assertEquals(2, adaptiveCardColumns(728f, 340f, gapDp = 16f))
        assertEquals(1, adaptiveCardColumns(840f, 340f * 1.3f))
    }
}
