package com.codexbar.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/** Native slider gestures, snapping and accessibility, with a lifted glass thumb and slim track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexBarSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null) {
    if (LocalCodexBarThemeProfile.current.style != AppThemeStyle.LIQUID_GLASS) {
        Slider(value, onValueChange, modifier, enabled, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val lift by animateFloatAsState(if (pressed || dragged) 1f else 0f, label = "slider_lift")
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Slider(value, onValueChange, modifier.heightIn(min = 48.dp), enabled, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished, interactionSource = interaction,
        thumb = {
            GlassSurface(LocalGlassCardBackdrop.current, CircleShape, Color.White.copy(alpha = 0.90f - lift * 0.30f),
                Color.White, Color.White.copy(alpha = 0.95f),
                Modifier.size(28.dp).graphicsLayer { scaleX = 1f + lift * 0.22f; scaleY = 1f + lift * 0.22f; alpha = if (enabled) 1f else 0.5f },
                blurRadius = 1.dp, refraction = 0.45f + lift * 0.35f, elevation = 3.dp) {}
        },
        track = {
            val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val middle = size.height / 2
                val start = if (rtl) size.width else 0f
                val end = if (rtl) size.width * (1f - fraction) else size.width * fraction
                drawLine(colors.onSurface.copy(alpha = 0.13f), Offset(0f, middle), Offset(size.width, middle), size.height, StrokeCap.Round)
                drawLine(colors.primary.copy(alpha = if (enabled) 0.90f else 0.35f), Offset(start, middle), Offset(end, middle), size.height, StrokeCap.Round)
            }
        })
}
