package com.codexbar.android.core.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRenderPolicyTest {
    @Test fun `347 by 69 accommodates three complete service rows`() {
        assertEquals(3, WidgetRenderPolicy.rowCount(69f))
        for (height in listOf(40f, 48f, 56f, 69f, 80f, 120f)) {
            assertTrue(WidgetRenderPolicy.rowCount(height) * 18 + 8 <= height)
        }
    }
    @Test fun `larger fonts reduce density without clipping rows`() {
        assertEquals(2, WidgetRenderPolicy.rowCount(69f, 1.2f))
        assertEquals(6, WidgetRenderPolicy.rowCount(500f))
    }
}
