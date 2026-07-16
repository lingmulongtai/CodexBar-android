package com.codexbar.android.core.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRenderPolicyTest {
    @Test
    fun `service count is bounded at every widget height`() {
        assertEquals(1, WidgetRenderPolicy.maxServices(90))
        assertEquals(2, WidgetRenderPolicy.maxServices(120))
        assertEquals(3, WidgetRenderPolicy.maxServices(260))
    }

    @Test
    fun `configured rows are clamped to the current widget size`() {
        assertEquals(1, WidgetRenderPolicy.maxRows(heightDp = 90, configuredRows = 6))
        assertEquals(2, WidgetRenderPolicy.maxRows(heightDp = 120, configuredRows = 6))
        assertEquals(4, WidgetRenderPolicy.maxRows(heightDp = 260, configuredRows = 6))
        assertEquals(1, WidgetRenderPolicy.maxRows(heightDp = 260, configuredRows = 0))
    }
}
