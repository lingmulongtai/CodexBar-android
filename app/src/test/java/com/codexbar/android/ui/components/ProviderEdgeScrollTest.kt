package com.codexbar.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderEdgeScrollTest {
    @Test fun `the same physical gesture has the same edge behavior across screen densities`() {
        for (density in listOf(1f, 2f, 2.75f, 4f)) {
            fun delta(dp: Float) = providerEdgeScrollDelta(dp * density, 0, (1000 * density).toInt(), density) / density
            assertEquals(-6f, delta(20f), 0.001f)
            assertEquals(0f, delta(80f), 0.001f)
            assertEquals(0f, delta(920f), 0.001f)
            assertEquals(6f, delta(980f), 0.001f)
        }
    }
}
