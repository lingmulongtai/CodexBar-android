package com.codexbar.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppThemeStyle

/**
 * What each design changes beyond the color scheme: its surfaces, the type used for readings, its
 * state colors and whether navigation floats over the content.
 */
data class CodexBarThemeProfile(
    val style: AppThemeStyle,
    val isDark: Boolean,
    val backgroundBrush: Brush,
    val navigationContainerColor: Color,
    val topBarContainerColor: Color,
    val cardColor: Color,
    val cardBorderColor: Color,
    val heroContainerColor: Color,
    val onHeroContainerColor: Color,
    val numeralFamily: FontFamily,
    /** A fixed color for healthy quota; null keeps each provider's own accent. */
    val goodColor: Color?,
    val warningColor: Color,
    val criticalColor: Color,
    val floatingNavigation: Boolean
)

val LocalCodexBarThemeProfile = staticCompositionLocalOf {
    CodexBarThemeProfile(
        style = AppThemeStyle.MATERIAL_3,
        isDark = false,
        backgroundBrush = SolidColor(Color.Transparent),
        navigationContainerColor = Color.Transparent,
        topBarContainerColor = Color.Transparent,
        cardColor = Color.White,
        cardBorderColor = Color.Transparent,
        heroContainerColor = Color.White,
        onHeroContainerColor = Color.Black,
        numeralFamily = FontFamily.Default,
        goodColor = null,
        warningColor = Color(0xFF8A4D00),
        criticalColor = Color(0xFFBA1A1A),
        floatingNavigation = false
    )
}

internal fun themeProfile(
    style: AppThemeStyle,
    colors: ColorScheme,
    darkTheme: Boolean
): CodexBarThemeProfile {
    return when (style) {
        AppThemeStyle.MATERIAL_3 -> {
            val page = if (darkTheme) colors.surface else colors.surfaceContainerLow
            CodexBarThemeProfile(
                style = style,
                isDark = darkTheme,
                backgroundBrush = SolidColor(page),
                navigationContainerColor = colors.surfaceContainer,
                topBarContainerColor = page,
                cardColor = if (darkTheme) colors.surfaceContainer else colors.surfaceContainerLowest,
                cardBorderColor = Color.Transparent,
                heroContainerColor = colors.primaryContainer,
                onHeroContainerColor = colors.onPrimaryContainer,
                numeralFamily = FontFamily.Default,
                goodColor = null,
                warningColor = if (darkTheme) Color(0xFFFFB95C) else Color(0xFF8A4D00),
                criticalColor = colors.error,
                floatingNavigation = false
            )
        }
        AppThemeStyle.LIQUID_GLASS -> CodexBarThemeProfile(
            style = style,
            isDark = darkTheme,
            backgroundBrush = Brush.linearGradient(
                if (darkTheme) listOf(Color(0xFF111B36), Color(0xFF29234A), Color(0xFF162C47))
                else listOf(Color(0xFFDCEBFF), Color(0xFFEEE7F8), Color(0xFFF7E9EF), Color(0xFFD8E9FC))
            ),
            navigationContainerColor = colors.surfaceContainerLow,
            topBarContainerColor = colors.background,
            cardColor = colors.surfaceContainerLow,
            cardBorderColor = Color.Transparent,
            heroContainerColor = colors.surfaceContainerLow,
            onHeroContainerColor = colors.onSurface,
            numeralFamily = InterFamily,
            goodColor = null,
            warningColor = if (darkTheme) Color(0xFFFFB340) else Color(0xFFB45309),
            criticalColor = colors.error,
            floatingNavigation = true
        )

    }
}

internal fun shapesFor(style: AppThemeStyle): Shapes = when (style) {
    AppThemeStyle.MATERIAL_3 -> CodexBarShapes
    AppThemeStyle.LIQUID_GLASS -> Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(34.dp)
    )
}
