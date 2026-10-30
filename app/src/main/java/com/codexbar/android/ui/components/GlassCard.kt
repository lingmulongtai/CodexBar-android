package com.codexbar.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/** Background-only source: a card must never sample the layer containing the card itself. */
val LocalGlassCardBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
fun GlassCard(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    GlassSurface(
        backdrop = LocalGlassCardBackdrop.current,
        shape = shape,
        tint = profile.cardColor.copy(alpha = if (profile.isDark) 0.70f else 0.68f),
        fallback = profile.cardColor,
        rim = Color.White.copy(alpha = if (profile.isDark) 0.20f else 0.72f),
        modifier = modifier,
        blurRadius = 12.dp,
        refraction = 0.18f,
        elevation = 3.dp
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onSurface,
            border = border,
            content = content
        )
    }
}
