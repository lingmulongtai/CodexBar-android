package com.codexbar.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.codexbar.android.core.domain.model.AppThemeStyle

@Composable
fun CodexBarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    style: AppThemeStyle = AppThemeStyle.MATERIAL_3,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        style == AppThemeStyle.MATERIAL_3 &&
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        style == AppThemeStyle.MATERIAL_3 && darkTheme -> CodexBarDarkColors
        style == AppThemeStyle.MATERIAL_3 -> CodexBarLightColors
        style == AppThemeStyle.LIQUID_GLASS && darkTheme -> LiquidGlassDarkColors
        style == AppThemeStyle.LIQUID_GLASS -> LiquidGlassLightColors
        style == AppThemeStyle.WINUI_3 && darkTheme -> WinUiDarkColors
        style == AppThemeStyle.WINUI_3 -> WinUiLightColors
        style == AppThemeStyle.AURORA && darkTheme -> AuroraDarkColors
        else -> AuroraLightColors
    }
    val profile = themeProfile(style, colorScheme, darkTheme)

    CompositionLocalProvider(LocalCodexBarThemeProfile provides profile) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapesFor(style),
            typography = CodexBarTypography,
            content = content
        )
    }
}

private val LiquidGlassLightColors = lightColorScheme(
    primary = Color(0xFF006493),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6FF),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = Color(0xFF66558D),
    secondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFF006A65),
    background = Color(0xFFF4FAFF),
    onBackground = Color(0xFF14202A),
    surface = Color(0xFFF8FCFF),
    onSurface = Color(0xFF14202A),
    surfaceVariant = Color(0xFFDCE8F2),
    onSurfaceVariant = Color(0xFF40505D),
    surfaceContainer = Color(0xFFE9F3FA),
    surfaceContainerLow = Color(0xFFF0F8FD),
    surfaceContainerHigh = Color(0xFFDFEBF3),
    outline = Color(0xFF6F7F8C),
    outlineVariant = Color(0xFFBECCD7)
)

private val LiquidGlassDarkColors = darkColorScheme(
    primary = Color(0xFF8DCDFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B70),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFD1BCFF),
    secondaryContainer = Color(0xFF4E3E73),
    tertiary = Color(0xFF75DBD2),
    background = Color(0xFF071521),
    onBackground = Color(0xFFDDEAF4),
    surface = Color(0xFF0D1C29),
    onSurface = Color(0xFFDDEAF4),
    surfaceVariant = Color(0xFF354653),
    onSurfaceVariant = Color(0xFFB9C9D5),
    surfaceContainer = Color(0xFF172735),
    surfaceContainerLow = Color(0xFF10202D),
    surfaceContainerHigh = Color(0xFF21313F),
    outline = Color(0xFF83939F),
    outlineVariant = Color(0xFF3A4A57)
)

private val WinUiLightColors = lightColorScheme(
    primary = Color(0xFF0067C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E9F8),
    onPrimaryContainer = Color(0xFF002E50),
    secondary = Color(0xFF4F6070),
    secondaryContainer = Color(0xFFDDE5EC),
    tertiary = Color(0xFF006B5E),
    background = Color(0xFFF3F3F3),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainer = Color(0xFFEFEFEF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFE9E9E9),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFD1D1D1)
)

private val WinUiDarkColors = darkColorScheme(
    primary = Color(0xFF60CDFF),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF004D67),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = Color(0xFFB8C8D6),
    secondaryContainer = Color(0xFF354553),
    tertiary = Color(0xFF63DAC7),
    background = Color(0xFF202020),
    onBackground = Color(0xFFF3F3F3),
    surface = Color(0xFF272727),
    onSurface = Color(0xFFF3F3F3),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFD0D0D0),
    surfaceContainer = Color(0xFF2C2C2C),
    surfaceContainerLow = Color(0xFF252525),
    surfaceContainerHigh = Color(0xFF333333),
    outline = Color(0xFF999999),
    outlineVariant = Color(0xFF454545)
)

private val AuroraLightColors = lightColorScheme(
    primary = Color(0xFF5934E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF19005F),
    secondary = Color(0xFF006B60),
    secondaryContainer = Color(0xFF75F8E2),
    tertiary = Color(0xFFA5005A),
    tertiaryContainer = Color(0xFFFFD8E7),
    background = Color(0xFFF8F6FF),
    onBackground = Color(0xFF1B1830),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1B1830),
    surfaceVariant = Color(0xFFE8E0F0),
    onSurfaceVariant = Color(0xFF4B4553),
    surfaceContainer = Color(0xFFF1ECF7),
    surfaceContainerLow = Color(0xFFF8F3FD),
    surfaceContainerHigh = Color(0xFFEBE5F1),
    outline = Color(0xFF7C7485),
    outlineVariant = Color(0xFFCEC5D5)
)

private val AuroraDarkColors = darkColorScheme(
    primary = Color(0xFFBDAAFF),
    onPrimary = Color(0xFF2B008B),
    primaryContainer = Color(0xFF431AC4),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF58DBC5),
    secondaryContainer = Color(0xFF005048),
    tertiary = Color(0xFFFFB0D0),
    tertiaryContainer = Color(0xFF7F0043),
    background = Color(0xFF070612),
    onBackground = Color(0xFFE8E0F0),
    surface = Color(0xFF100E1C),
    onSurface = Color(0xFFE8E0F0),
    surfaceVariant = Color(0xFF494453),
    onSurfaceVariant = Color(0xFFCBC3D2),
    surfaceContainer = Color(0xFF1A1728),
    surfaceContainerLow = Color(0xFF12101F),
    surfaceContainerHigh = Color(0xFF252133),
    outline = Color(0xFF958D9D),
    outlineVariant = Color(0xFF494453)
)
