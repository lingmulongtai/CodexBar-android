package com.codexbar.android.ui.theme

import androidx.compose.material3.lightColorScheme
import com.codexbar.android.core.domain.model.AppThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexBarThemeProfileTest {
    @Test
    fun `current Material profile preserves provider card design`() {
        val profile = themeProfile(
            style = AppThemeStyle.MATERIAL_3,
            colors = lightColorScheme(),
            darkTheme = false
        )

        assertTrue(profile.usesProviderCardShapes)
        assertEquals(1f, profile.serviceCardContainerAlpha)
    }

    @Test
    fun `alternate profiles use distinct layered card treatments`() {
        val profiles = AppThemeStyle.entries
            .filterNot { it == AppThemeStyle.MATERIAL_3 }
            .map { style ->
                themeProfile(style, lightColorScheme(), darkTheme = false)
            }

        profiles.forEach { profile ->
            assertFalse(profile.usesProviderCardShapes)
            assertTrue(profile.serviceCardContainerAlpha < 1f)
        }
        assertEquals(profiles.size, profiles.map { it.serviceCardContainerAlpha }.distinct().size)
        assertNotEquals(shapesFor(AppThemeStyle.LIQUID_GLASS), shapesFor(AppThemeStyle.WINUI_3))
    }
}
