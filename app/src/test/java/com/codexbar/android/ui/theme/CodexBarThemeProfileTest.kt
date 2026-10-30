package com.codexbar.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.font.FontFamily
import com.codexbar.android.core.domain.model.AppThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexBarThemeProfileTest {
    @Test
    fun `Material profile keeps provider accents and docked navigation`() {
        val profile = themeProfile(AppThemeStyle.MATERIAL_3, lightColorScheme(), darkTheme = false)

        assertNull(profile.goodColor)
        assertFalse(profile.floatingNavigation)
        assertEquals(FontFamily.Default, profile.numeralFamily)
    }

    @Test
    fun `only Liquid Glass floats its navigation over content`() {
        AppThemeStyle.entries.forEach { style ->
            val profile = themeProfile(style, lightColorScheme(), darkTheme = false)
            assertEquals(style == AppThemeStyle.LIQUID_GLASS, profile.floatingNavigation)
        }
    }

    @Test
    fun `every design has its own shape scale`() {
        val shapes = AppThemeStyle.entries.map(::shapesFor)
        assertEquals(shapes.size, shapes.distinct().size)
    }
}
