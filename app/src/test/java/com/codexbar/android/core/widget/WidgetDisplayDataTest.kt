package com.codexbar.android.core.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetDisplayDataTest {
    @Test fun `single row keeps the tightest quota even when it is not the first window`() {
        val metrics = listOf(WidgetMetric("5-Hour", .8f, null, null),
            WidgetMetric("Weekly", .1f, null, null))
        assertEquals("Weekly", selectWidgetMetrics(metrics, 1).single().label)
        assertEquals("10%", selectWidgetMetrics(metrics, 1).single().percent)
    }
    @Test fun `unknown metrics cannot displace an exhausted quota`() {
        val metrics = listOf(WidgetMetric("Unknown", null, null, null),
            WidgetMetric("Weekly", 0f, null, null))
        assertEquals("0%", selectWidgetMetrics(metrics, 1).single().percent)
    }
}
