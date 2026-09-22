package com.codexbar.android.core.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetRenderPolicyTest {
    @Test
    fun `service count is bounded at every widget height`() {
        assertEquals(1, WidgetRenderPolicy.maxServices(90))
        assertEquals(1, WidgetRenderPolicy.maxServices(260))
        assertEquals(2, WidgetRenderPolicy.maxServices(360))
    }

    @Test
    fun `configured rows are clamped to the current widget size`() {
        assertEquals(1, WidgetRenderPolicy.maxRows(heightDp = 90, configuredRows = 6))
        assertEquals(1, WidgetRenderPolicy.maxRows(heightDp = 180, configuredRows = 6))
        assertEquals(2, WidgetRenderPolicy.maxRows(heightDp = 260, configuredRows = 6))
        assertEquals(1, WidgetRenderPolicy.maxRows(heightDp = 260, configuredRows = 0))
    }

    @Test
    fun `thin Niagara sizes reserve room for complete quota rows`() {
        for (height in listOf(40, 48, 60, 80, 120, 179)) {
            assertTrue(WidgetRenderPolicy.isCompact(320, height))
            assertTrue(WidgetRenderPolicy.compactServices(height) * 24 + 8 <= height)
        }
        assertEquals(1, WidgetRenderPolicy.compactServices(40))
        assertEquals(2, WidgetRenderPolicy.compactServices(60))
        assertTrue(WidgetRenderPolicy.isCompact(140, 240))
        assertFalse(WidgetRenderPolicy.isCompact(320, 180))
    }
}
