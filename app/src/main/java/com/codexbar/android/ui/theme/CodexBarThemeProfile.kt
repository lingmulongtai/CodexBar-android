package com.codexbar.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppThemeStyle

data class CodexBarThemeProfile(
    val style: AppThemeStyle,
    val backgroundBrush: Brush,
    val navigationContainerColor: Color,
    val topBarContainerColor: Color,
    val serviceCardContainerAlpha: Float,
    val serviceCardBorderAlpha: Float,
    val serviceCardElevation: Dp,
    val usesProviderCardShapes: Boolean
)

val LocalCodexBarThemeProfile = staticCompositionLocalOf {
    CodexBarThemeProfile(
        style = AppThemeStyle.MATERIAL_3,
        backgroundBrush = SolidColor(Color.Transparent),
        navigationContainerColor = Color.Transparent,
        topBarContainerColor = Color.Transparent,
        serviceCardContainerAlpha = 1f,
        serviceCardBorderAlpha = 0.22f,
        serviceCardElevation = 1.dp,
        usesProviderCardShapes = true
    )
}

internal fun themeProfile(
    style: AppThemeStyle,
    colors: ColorScheme,
    darkTheme: Boolean
): CodexBarThemeProfile {
    return when (style) {
        AppThemeStyle.MATERIAL_3 -> CodexBarThemeProfile(
            style = style,
            backgroundBrush = SolidColor(colors.background),
            navigationContainerColor = colors.surfaceContainer,
            topBarContainerColor = colors.surface,
            serviceCardContainerAlpha = 1f,
            serviceCardBorderAlpha = 0.22f,
            serviceCardElevation = 1.dp,
            usesProviderCardShapes = true
        )
        AppThemeStyle.LIQUID_GLASS -> CodexBarThemeProfile(
            style = style,
            backgroundBrush = Brush.linearGradient(
                if (darkTheme) {
                    listOf(Color(0xFF071521), Color(0xFF15263A), Color(0xFF241C3D))
                } else {
                    listOf(Color(0xFFE8F7FF), Color(0xFFF7F9FF), Color(0xFFF5ECFF))
                }
            ),
            navigationContainerColor = colors.surfaceContainer.copy(alpha = 0.78f),
            topBarContainerColor = colors.surface.copy(alpha = 0.70f),
            serviceCardContainerAlpha = 0.74f,
            serviceCardBorderAlpha = 0.36f,
            serviceCardElevation = 5.dp,
            usesProviderCardShapes = false
        )
        AppThemeStyle.WINUI_3 -> CodexBarThemeProfile(
            style = style,
            backgroundBrush = Brush.verticalGradient(
                listOf(colors.background, colors.surfaceContainerLow)
            ),
            navigationContainerColor = colors.surfaceContainer.copy(alpha = 0.94f),
            topBarContainerColor = colors.surface.copy(alpha = 0.92f),
            serviceCardContainerAlpha = 0.94f,
            serviceCardBorderAlpha = 0.24f,
            serviceCardElevation = 2.dp,
            usesProviderCardShapes = false
        )
        AppThemeStyle.AURORA -> CodexBarThemeProfile(
            style = style,
            backgroundBrush = Brush.linearGradient(
                if (darkTheme) {
                    listOf(Color(0xFF070612), Color(0xFF10152A), Color(0xFF241039))
                } else {
                    listOf(Color(0xFFF1F7FF), Color(0xFFF6F1FF), Color(0xFFFFF0FA))
                }
            ),
            navigationContainerColor = colors.surfaceContainer.copy(alpha = 0.86f),
            topBarContainerColor = colors.surface.copy(alpha = 0.76f),
            serviceCardContainerAlpha = 0.86f,
            serviceCardBorderAlpha = 0.42f,
            serviceCardElevation = 4.dp,
            usesProviderCardShapes = false
        )
    }
}

internal fun shapesFor(style: AppThemeStyle): Shapes = when (style) {
    AppThemeStyle.MATERIAL_3 -> CodexBarShapes
    AppThemeStyle.LIQUID_GLASS -> Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(18.dp),
        medium = RoundedCornerShape(26.dp),
        large = RoundedCornerShape(34.dp),
        extraLarge = RoundedCornerShape(44.dp)
    )
    AppThemeStyle.WINUI_3 -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp)
    )
    AppThemeStyle.AURORA -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp)
    )
}
