package com.codexbar.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val LocalTopLevelSlideDirection = staticCompositionLocalOf { 0 }
val LocalGlassHeaderHeight = staticCompositionLocalOf<Animatable<Float, AnimationVector1D>?> { null }
val LocalGlassHeaderTitle = staticCompositionLocalOf<MutableState<String?>?> { null }

/** Records the wallpaper and scrolling content without recording the header that samples them. */
@Composable
fun GlassHeaderOverlay(
    header: @Composable () -> Unit,
    initialExtraHeight: Dp = 0.dp,
    content: @Composable (Dp) -> Unit
) {
    val backdrop = rememberGlassBackdrop()
    val wallpaper = LocalGlassCardBackdrop.current
    val density = LocalDensity.current
    val initialHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp + initialExtraHeight
    var measuredHeight by remember { mutableStateOf<Dp?>(null) }
    val direction = LocalTopLevelSlideDirection.current
    val offset = remember { Animatable(direction.toFloat()) }
    LaunchedEffect(offset) { offset.animateTo(0f, tween(260)) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop, wallpaper)) {
            Box(Modifier.fillMaxSize().offset {
                IntOffset((offset.value * 36.dp.toPx()).roundToInt(), 0)
            }) { content(measuredHeight ?: initialHeight) }
        }
        CompositionLocalProvider(LocalGlassHeaderBackdrop provides backdrop) {
            Box(Modifier.align(Alignment.TopCenter).onSizeChanged {
                measuredHeight = with(density) { it.height.toDp() }
            }) { header() }
        }
    }
}
