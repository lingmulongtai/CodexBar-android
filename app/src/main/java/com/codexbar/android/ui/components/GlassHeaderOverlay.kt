package com.codexbar.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalGlassHeaderHeight = staticCompositionLocalOf<Animatable<Float, AnimationVector1D>?> { null }
val LocalGlassHeaderNaturalHeight = staticCompositionLocalOf<(Int) -> Unit> { {} }
internal val NaturalGlassHeaderHeight = HorizontalAlignmentLine(::maxOf)
internal val VisibleGlassHeaderHeight = HorizontalAlignmentLine(::maxOf)

internal fun stableHeaderContentTop(natural: Int, visible: Int, total: Int): Int =
    natural.coerceAtLeast(0) + (total - visible).coerceAtLeast(0)

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
    val pinned = LocalPinnedGlassHeader.current
    val currentHeader = rememberUpdatedState(header)
    if (pinned != null) DisposableEffect(pinned) {
        pinned.content = { currentHeader.value() }
        onDispose { pinned.content = null }
    }
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val top = subcompose("header") {
            CompositionLocalProvider(
                LocalGlassHeaderBackdrop provides backdrop,
                LocalGlassHeaderHeight provides if (pinned != null) null else LocalGlassHeaderHeight.current
            ) { Box { header() } }
        }.single().measure(constraints.copy(minHeight = 0))
        val natural = top[NaturalGlassHeaderHeight].takeUnless { it == AlignmentLine.Unspecified } ?: top.height
        val visible = top[VisibleGlassHeaderHeight].takeUnless { it == AlignmentLine.Unspecified } ?: top.height
        val contentTop = with(density) { stableHeaderContentTop(natural, visible, top.height).toDp() }
        val body = subcompose("body") {
            Box(Modifier.fillMaxSize().then(if (pinned == null) Modifier.glassBackdropSource(backdrop, wallpaper) else Modifier)) {
                content(contentTop)
            }
        }.single().measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            body.place(0, 0)
            if (pinned == null || pinned.content == null) top.place(0, 0)
        }
    }
}
