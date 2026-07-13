package com.codexbar.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaSeverity

object CodexBarSpacing {
    val xsmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xlarge = 24.dp
}

val CodexBarShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object CodexBarStateColors {
    val Warning = Color(0xFFFFB300)
    val Success = Color(0xFF2E7D32)
    val Unknown = Color(0xFF6F6F6F)

    @Composable
    fun severityColor(severity: QuotaSeverity): Color {
        val scheme = MaterialTheme.colorScheme
        return when (severity) {
            QuotaSeverity.Critical -> scheme.error
            QuotaSeverity.Warning -> Warning
            QuotaSeverity.Good -> scheme.primary
            QuotaSeverity.Unknown -> scheme.outline
            QuotaSeverity.Redacted -> scheme.surfaceVariant
        }
    }

    fun providerAccent(service: AiService): Color = Color(service.brandColor)
}

val CodexBarLightColors: ColorScheme
    @Composable get() = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF006D5B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF8CF8DC),
        onPrimaryContainer = Color(0xFF002019),
        secondary = Color(0xFF4B635B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCDE9DD),
        onSecondaryContainer = Color(0xFF082019),
        tertiary = Color(0xFF406376),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC4E7FF),
        onTertiaryContainer = Color(0xFF001E2C),
        error = Color(0xFFBA1A1A),
        surface = Color(0xFFFBFDF9),
        onSurface = Color(0xFF191C1A),
        surfaceVariant = Color(0xFFDCE5DE),
        onSurfaceVariant = Color(0xFF404943),
        outline = Color(0xFF707973)
    )

val CodexBarDarkColors: ColorScheme
    @Composable get() = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFF70DBC1),
        onPrimary = Color(0xFF00382E),
        primaryContainer = Color(0xFF005143),
        onPrimaryContainer = Color(0xFF8CF8DC),
        secondary = Color(0xFFB1CCC1),
        onSecondary = Color(0xFF1D352E),
        secondaryContainer = Color(0xFF334B44),
        onSecondaryContainer = Color(0xFFCDE9DD),
        tertiary = Color(0xFFA8CBE1),
        onTertiary = Color(0xFF0C3446),
        tertiaryContainer = Color(0xFF274B5D),
        onTertiaryContainer = Color(0xFFC4E7FF),
        error = Color(0xFFFFB4AB),
        surface = Color(0xFF101411),
        onSurface = Color(0xFFE0E3DF),
        surfaceVariant = Color(0xFF404943),
        onSurfaceVariant = Color(0xFFC0C9C1),
        outline = Color(0xFF8A938C)
    )
