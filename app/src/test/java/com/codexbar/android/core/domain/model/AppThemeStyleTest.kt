package com.codexbar.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeStyleTest {
    @Test
    fun `restores every persisted theme style`() {
        AppThemeStyle.entries.forEach { style ->
            assertEquals(style, AppThemeStyle.fromStoredValue(style.name))
        }
    }

    @Test
    fun `falls back to current Material design for unknown values`() {
        assertEquals(AppThemeStyle.MATERIAL_3, AppThemeStyle.fromStoredValue(null))
        assertEquals(AppThemeStyle.MATERIAL_3, AppThemeStyle.fromStoredValue("FUTURE_THEME"))
    }
}
