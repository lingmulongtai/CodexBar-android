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
    val xxlarge = 32.dp
    val huge = 40.dp
}

val CodexBarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp)
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
        primary = Color(0xFF4355B9),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDEE0FF),
        onPrimaryContainer = Color(0xFF0A1764),
        inversePrimary = Color(0xFFBBC3FF),
        secondary = Color(0xFF6B5778),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3DAFF),
        onSecondaryContainer = Color(0xFF251431),
        tertiary = Color(0xFF006879),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFA8EDFF),
        onTertiaryContainer = Color(0xFF001F26),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBF8FF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFBF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE4E1EC),
        onSurfaceVariant = Color(0xFF46464F),
        surfaceTint = Color(0xFF4355B9),
        inverseSurface = Color(0xFF303036),
        inverseOnSurface = Color(0xFFF2F0F8),
        outline = Color(0xFF777680),
        outlineVariant = Color(0xFFC7C5D0),
        scrim = Color.Black,
        surfaceBright = Color(0xFFFBF8FF),
        surfaceDim = Color(0xFFDBD9E1),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F2FA),
        surfaceContainer = Color(0xFFEFECF4),
        surfaceContainerHigh = Color(0xFFE9E7EF),
        surfaceContainerHighest = Color(0xFFE4E1E9)
    )

val CodexBarDarkColors: ColorScheme
    @Composable get() = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFFBBC3FF),
        onPrimary = Color(0xFF102978),
        primaryContainer = Color(0xFF293D9F),
        onPrimaryContainer = Color(0xFFDEE0FF),
        inversePrimary = Color(0xFF4355B9),
        secondary = Color(0xFFDDBEEB),
        onSecondary = Color(0xFF3B2948),
        secondaryContainer = Color(0xFF533F60),
        onSecondaryContainer = Color(0xFFF3DAFF),
        tertiary = Color(0xFF52D7F0),
        onTertiary = Color(0xFF00363F),
        tertiaryContainer = Color(0xFF004E5B),
        onTertiaryContainer = Color(0xFFA8EDFF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF46464F),
        onSurfaceVariant = Color(0xFFC7C5D0),
        surfaceTint = Color(0xFFBBC3FF),
        inverseSurface = Color(0xFFE4E1E9),
        inverseOnSurface = Color(0xFF303036),
        outline = Color(0xFF91909A),
        outlineVariant = Color(0xFF46464F),
        scrim = Color.Black,
        surfaceBright = Color(0xFF39383E),
        surfaceDim = Color(0xFF131318),
        surfaceContainerLowest = Color(0xFF0E0E13),
        surfaceContainerLow = Color(0xFF1B1B21),
        surfaceContainer = Color(0xFF1F1F25),
        surfaceContainerHigh = Color(0xFF2A292F),
        surfaceContainerHighest = Color(0xFF35343A)
    )
