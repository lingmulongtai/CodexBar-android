package com.codexbar.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeStyleTest {
    @Test
    fun `current and retired saved designs all open Liquid Glass`() {
        listOf(null, "LIQUID_GLASS", "MATERIAL_3", "INSTRUMENT", "WINUI_3", "AURORA", "FUTURE_THEME")
            .forEach { assertEquals(AppThemeStyle.LIQUID_GLASS, AppThemeStyle.fromStoredValue(it)) }
    }
}
