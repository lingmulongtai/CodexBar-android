package com.codexbar.android.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.codexbar.android.core.domain.model.AppThemeStyle

@Composable
fun CodexBarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    style: AppThemeStyle = AppThemeStyle.MATERIAL_3,
    content: @Composable () -> Unit
) {
    // Instrument is an always-dark design; the others follow the system appearance.
    val useDarkTheme = style == AppThemeStyle.INSTRUMENT || darkTheme
    val colorScheme = when (style) {
        AppThemeStyle.MATERIAL_3 -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            useDarkTheme -> CodexBarDarkColors
            else -> CodexBarLightColors
        }
        AppThemeStyle.LIQUID_GLASS -> if (useDarkTheme) LiquidGlassDarkColors else LiquidGlassLightColors
        AppThemeStyle.INSTRUMENT -> InstrumentColors
    }
    val profile = themeProfile(style, colorScheme, useDarkTheme)
    SystemBarAppearance(darkTheme = useDarkTheme)

    CompositionLocalProvider(LocalCodexBarThemeProfile provides profile) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapesFor(style),
            typography = typographyFor(style)
        ) {
            // Screens draw directly on the design's background, outside any Surface.
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
        }
    }
}

/** Keeps status and navigation bar icons legible when a design is darker than the system. */
@Composable
private fun SystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val LiquidGlassLightColors = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A2A6B),
    inversePrimary = Color(0xFF8AB0FF),
    secondary = Color(0xFF5E5E66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9EE),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF7A4FD6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFE5FF),
    onTertiaryContainer = Color(0xFF2A0F5C),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFDE3E1),
    onErrorContainer = Color(0xFF5C0B05),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceTint = Color.White,
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFD1D1D6),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3E3E8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF7F7FA),
    surfaceContainerHigh = Color(0xFFE9E9EE),
    surfaceContainerHighest = Color(0xFFE3E3E8)
)

private val LiquidGlassDarkColors = darkColorScheme(
    primary = Color(0xFF6E9BFF),
    onPrimary = Color(0xFF00215E),
    primaryContainer = Color(0xFF1D3A7A),
    onPrimaryContainer = Color(0xFFDCE6FF),
    inversePrimary = Color(0xFF2F6BFF),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFFC3A6FF),
    onTertiary = Color(0xFF2A0F5C),
    tertiaryContainer = Color(0xFF3B2470),
    onTertiaryContainer = Color(0xFFEFE5FF),
    error = Color(0xFFFF6961),
    onError = Color(0xFF450704),
    errorContainer = Color(0xFF4A1210),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    surface = Color.Black,
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceTint = Color.White,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF38383A),
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF232326),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C)
)

private val InstrumentColors = darkColorScheme(
    primary = InstrumentMint,
    onPrimary = Color(0xFF032816),
    primaryContainer = Color(0xFF123A29),
    onPrimaryContainer = Color(0xFFA5F4CB),
    inversePrimary = Color(0xFF0F7A4D),
    secondary = Color(0xFFA0A6B2),
    onSecondary = Color(0xFF15181D),
    secondaryContainer = Color(0xFF23262E),
    onSecondaryContainer = Color(0xFFECEEF2),
    tertiary = InstrumentAmber,
    onTertiary = Color(0xFF2E1B00),
    tertiaryContainer = Color(0xFF3B2A0E),
    onTertiaryContainer = Color(0xFFFFDDAA),
    error = InstrumentCoral,
    onError = Color(0xFF3A0600),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = Color(0xFFFFB4AB),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF0B0C0F),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF1C1F26),
    onSurfaceVariant = Color(0xFFA0A6B2),
    surfaceTint = Color.White,
    inverseSurface = Color(0xFFECEEF2),
    inverseOnSurface = Color(0xFF14161B),
    outline = Color(0xFF4A4F5A),
    outlineVariant = Color(0xFF262A33),
    surfaceBright = Color(0xFF262A33),
    surfaceDim = Color(0xFF0B0C0F),
    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF14161B),
    surfaceContainer = Color(0xFF181A20),
    surfaceContainerHigh = Color(0xFF1F2229),
    surfaceContainerHighest = Color(0xFF262A33)
)
