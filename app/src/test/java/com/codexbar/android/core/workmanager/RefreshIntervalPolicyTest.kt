package com.codexbar.android.core.workmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshIntervalPolicyTest {
    @Test
    fun `manual refresh remains disabled periodic work`() {
        assertEquals(0L, RefreshIntervalPolicy.normalize(0L))
        assertEquals(0L, RefreshIntervalPolicy.normalize(-1L))
    }

    @Test
    fun `automatic refresh is bounded and rounded to five minutes`() {
        assertEquals(15L, RefreshIntervalPolicy.normalize(1L))
        assertEquals(15L, RefreshIntervalPolicy.normalize(17L))
        assertEquals(20L, RefreshIntervalPolicy.normalize(18L))
        assertEquals(55L, RefreshIntervalPolicy.normalize(55L))
        assertEquals(120L, RefreshIntervalPolicy.normalize(999L))
    }
}
