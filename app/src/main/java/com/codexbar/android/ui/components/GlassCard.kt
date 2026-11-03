package com.codexbar.android.ui.components

import com.codexbar.android.core.domain.model.GlassSurfaceGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/** Background-only source: a card must never sample the layer containing the card itself. */
val LocalGlassCardBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Screen content, recorded separately from the overlay that consumes it. */
val LocalGlassHeaderBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Children of a glass panel must not resample a sharper copy of its wallpaper. */
val LocalGlassPanelDepth = staticCompositionLocalOf { 0 }

@Composable
fun GlassCard(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val selectedState = selected
    val depth = LocalGlassPanelDepth.current
    GlassSurface(
        tuningGroup = GlassSurfaceGroup.CONTAINERS,
        backdrop = if (depth == 0) LocalGlassCardBackdrop.current else null,
        shape = shape,
        tint = profile.cardColor.copy(alpha = if (profile.isDark) 0.70f else 0.68f),
        fallback = if (depth == 0) profile.cardColor else Color.White.copy(alpha = if (profile.isDark) 0.07f else 0.35f),
        rim = Color.White.copy(alpha = if (profile.isDark) 0.20f else 0.72f),
        modifier = modifier,
        blurRadius = 12.dp,
        refraction = 0.18f,
        elevation = 3.dp
    ) {
        val cardModifier = Modifier.fillMaxWidth().semantics {
            if (selectedState != null) this.selected = selectedState
        }
        CompositionLocalProvider(LocalGlassPanelDepth provides depth + 1) {
            if (enabled) {
                Surface(onClick = onClick, modifier = cardModifier, shape = shape, color = Color.Transparent,
                    contentColor = colors.onSurface, border = border, content = content)
            } else {
                Surface(modifier = cardModifier, shape = shape, color = Color.Transparent,
                    contentColor = colors.onSurface, border = border, content = content)
            }
        }
    }
}
