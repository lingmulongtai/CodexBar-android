package com.codexbar.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
    val lift by animateFloatAsState(if (pressed || dragged) 1f else 0f, spring(stiffness = 600f), label = "slider_lift")
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Slider(value, onValueChange, modifier.heightIn(min = 48.dp), enabled, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished, interactionSource = interaction,
        thumb = {
            GlassSurface(LocalGlassCardBackdrop.current, CircleShape, Color.White.copy(alpha = 0.94f - lift * 0.48f),
                Color.White, Color.White.copy(alpha = 0.95f),
                Modifier.size(36.dp, 24.dp).graphicsLayer {
                    scaleX = 1f + lift * 0.60f; scaleY = 1f + lift * 0.60f
                    alpha = if (enabled) 1f else 0.5f
                }, blurRadius = 2.dp, refraction = 0.45f + lift * 0.35f, elevation = 3.dp) {
                Canvas(Modifier.fillMaxSize()) {
                    val middle = size.height / 2
                    val inset = 3.dp.toPx()
                    drawLine(colors.onSurface.copy(alpha = 0.18f * lift), Offset(inset, middle),
                        Offset(size.width - inset, middle), 8.dp.toPx(), StrokeCap.Round)
                    val from = if (rtl) size.width - inset else inset
                    drawLine(colors.primary.copy(alpha = 0.60f * lift), Offset(from, middle),
                        Offset(size.width / 2, middle), 8.dp.toPx(), StrokeCap.Round)
                }
            }
        },
        track = {
            val range = valueRange.endInclusive - valueRange.start
            val fraction = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val middle = size.height / 2
                val start = if (rtl) size.width else 0f
                val end = if (rtl) size.width * (1f - fraction) else size.width * fraction
                drawLine(colors.onSurface.copy(alpha = 0.13f), Offset(0f, middle), Offset(size.width, middle), size.height, StrokeCap.Round)
                drawLine(colors.primary.copy(alpha = if (enabled) 0.90f else 0.35f), Offset(start, middle), Offset(end, middle), size.height, StrokeCap.Round)
            }
        })
}
