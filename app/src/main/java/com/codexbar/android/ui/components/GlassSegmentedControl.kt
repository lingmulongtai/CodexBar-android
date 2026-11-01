package com.codexbar.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import kotlin.math.roundToInt

/** Glisse segmented control, with an independently recorded track and accessible 48 dp targets. */
@Composable
fun GlassSegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestSelect by rememberUpdatedState(onSelect)
    val latestSelected by rememberUpdatedState(selected)
    val lensBackdrop = rememberGlassBackdrop()
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var anchor by remember { mutableFloatStateOf(0f) }
    var downX by remember { mutableFloatStateOf(0f) }
    var movement by remember { mutableFloatStateOf(0f) }
    val target = dragPosition ?: pressedIndex?.toFloat() ?: selected.coerceIn(labels.indices).toFloat()
    val position by animateFloatAsState(target,
        spring(dampingRatio = if (dragPosition != null) 0.94f else 0.92f,
            stiffness = if (dragPosition != null) 2500f else 1024f), label = "segmented_position")
    val lift by animateFloatAsState(if (dragPosition != null || pressedIndex != null) 1f else 0f,
        spring(dampingRatio = 0.92f, stiffness = 676f), label = "segmented_lift")
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).selectableGroup()
        .pointerInput(labels.size, rtl) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                downX = down.position.x
                pressedIndex = glassTabPosition(down.position.x, size.width.toFloat(), labels.size, rtl).roundToInt()
                try { waitForUpOrCancellation() } finally { pressedIndex = null }
            }
        }
        .pointerInput(labels.size, rtl) {
            detectHorizontalDragGestures(
                onDragStart = {
                    anchor = glassTabPosition(downX, size.width.toFloat(), labels.size, rtl).roundToInt().toFloat()
                    movement = 0f
                    dragPosition = anchor
                },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    movement = change.position.x - downX
                    dragPosition = glassTabDragPosition(anchor, movement, size.width.toFloat() / labels.size, labels.size, rtl)
                },
                onDragEnd = {
                    val targetIndex = dragPosition?.roundToInt()
                    dragPosition = null
                    if (targetIndex != null && targetIndex != latestSelected) latestSelect(targetIndex)
                },
                onDragCancel = { dragPosition = null }
            )
        }) {
        val cell = maxWidth / labels.size
        val visual = if (rtl) labels.lastIndex - position else position
        val foreground = if (profile.isDark) Color.White else Color.Black
        Box(Modifier.fillMaxSize().glassBackdropSource(lensBackdrop)) {
            GlassSurface(LocalGlassCardBackdrop.current, CircleShape,
                if (profile.isDark) Color(0xFF282828).copy(alpha = 0.62f) else Color(0xFF767680).copy(alpha = 0.12f),
                colors.surfaceContainerHigh, if (profile.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth().height(32.dp).align(Alignment.Center),
                blurRadius = 3.dp, refraction = 0f, elevation = if (profile.isDark) 0.dp else 1.dp) {}
        }
        GlassSurface(lensBackdrop, CircleShape,
            if (profile.isDark) Color(0xFFEBEBF5).copy(alpha = 0.30f * (1f - lift))
            else Color.White.copy(alpha = 1f - lift * 0.97f),
            colors.surfaceContainerHigh, Color.White.copy(alpha = if (profile.isDark) 0.15f else 0.40f),
            modifier = Modifier.align(AbsoluteAlignment.TopLeft).width(cell - 4.dp).height(28.dp)
                .absoluteOffset { IntOffset((2.dp.toPx() + cell.toPx() * visual).roundToInt(), 10.dp.roundToPx()) }
                .graphicsLayer {
                    scaleX = 1f + lift * (24.dp.toPx() / (cell - 4.dp).toPx().coerceAtLeast(1f))
                    scaleY = 1f + lift * (16f / 28f)
                }, blurRadius = 0.1.dp, refraction = 0.55f * lift,
            elevation = lift.dp * if (profile.isDark) 0 else 4
        ) {}
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Box(Modifier.weight(1f).fillMaxHeight()
                    .then(if (focused) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
                    .selectable(selected = index == selected, onClick = { onSelect(index) }, role = Role.Tab,
                        interactionSource = interaction, indication = null), contentAlignment = Alignment.Center) {
                    Text(label, color = foreground, fontSize = 13.sp, lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
