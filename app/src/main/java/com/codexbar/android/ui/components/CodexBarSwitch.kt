package com.codexbar.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import kotlin.math.roundToInt

/** Glisse-style held lens, with native toggle semantics and a 48 dp touch target. */
@Composable
fun CodexBarSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val profile = LocalCodexBarThemeProfile.current
    if (profile.style != AppThemeStyle.LIQUID_GLASS) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestChecked by rememberUpdatedState(checked)
    val latestChange by rememberUpdatedState(onCheckedChange)
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var startX by remember { mutableFloatStateOf(0f) }
    var from by remember { mutableFloatStateOf(0f) }
    val settled by animateFloatAsState(dragPosition ?: if (checked) 1f else 0f,
        if (dragPosition != null) snap() else spring(stiffness = 700f), label = "switch_position")
    val lift by animateFloatAsState(if (pressed || dragPosition != null) 1f else 0f, spring(stiffness = 600f), label = "switch_lift")
    val position = dragPosition ?: settled
    val green = if (profile.isDark) Color(0xFF30D158) else Color(0xFF34C759)
    val grey = if (profile.isDark) Color(0xFF555563) else Color(0xFFB8BBC6)
    Box(
        modifier.size(64.dp, 48.dp)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .toggleable(checked, enabled = enabled, role = Role.Switch, interactionSource = interaction,
                indication = null, onValueChange = onCheckedChange)
            .pointerInput(enabled, rtl) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        startX = point.x
                        from = if (latestChecked) 1f else 0f
                        dragPosition = from
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = glassSwitchPosition(from, change.position.x - startX, 20.dp.toPx(), rtl)
                    },
                    onDragEnd = {
                        val next = dragPosition?.let { it > 0.5f }
                        dragPosition = null
                        if (next != null && next != latestChecked) latestChange(next)
                    },
                    onDragCancel = { dragPosition = null }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val track = lerp(grey, green, position.coerceIn(0f, 1f))
        Box(Modifier.size(60.dp, 26.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .background(track, CircleShape)) {
            val visual = if (rtl) 1f - position else position
            GlassSurface(
                backdrop = LocalGlassCardBackdrop.current, shape = CircleShape,
                tint = Color.White.copy(alpha = 0.94f - lift * 0.48f), fallback = Color.White,
                rim = Color.White.copy(alpha = 0.95f), blurRadius = 2.dp,
                refraction = 0.45f + lift * 0.35f, elevation = 2.dp,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft).size(36.dp, 24.dp)
                    .absoluteOffset { IntOffset((2.dp.toPx() + 20.dp.toPx() * visual).roundToInt(), 1.dp.roundToPx()) }
                    .graphicsLayer { scaleX = 1f + lift * 0.36f; scaleY = 1f + lift * 0.30f }
            ) {
                // The lifted lens reveals the colored track beneath it, as in Glisse.
                Box(Modifier.fillMaxWidth(0.90f).height(18.dp)
                    .background(track.copy(alpha = lift * 0.55f), CircleShape))
            }
        }
    }
}
