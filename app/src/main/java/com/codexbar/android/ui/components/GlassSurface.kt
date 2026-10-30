package com.codexbar.android.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Records the content that floating glass chrome covers, so the chrome can blur what is really
 * behind it instead of painting a translucent fill over nothing.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var originInRoot by mutableStateOf(Offset.Zero)
    internal var coordinateRoot by mutableStateOf<LayoutCoordinates?>(null)

    /** Bumped after each recording so glass panels redraw with what is now behind them. */
    internal var generation by mutableIntStateOf(0)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.originInRoot = it.positionInRoot(); backdrop.coordinateRoot = it.findRootCoordinates() }
    // Keep the source's display list when only a glass consumer needs redrawing. Without this
    // boundary, its generation write and the consumer's invalidation keep redrawing each other.
    .graphicsLayer()
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
        // Written without observing it, so the source never invalidates its own drawing.
        Snapshot.withoutReadObservation { backdrop.generation++ }
    }

private class PositionHolder {
    // Moving a panel over a static backdrop must resample it even if the source did not redraw.
    var value by mutableStateOf(Offset.Zero)
    var coordinateRoot by mutableStateOf<LayoutCoordinates?>(null)
}

/**
 * A backdrop lens with a directional specular rim. Android 13+ bends the real backdrop at the
 * edges; Android 12 uses backdrop blur, and older versions use the readable [fallback].
 * Set [refraction] and [elevation] to zero for flat, full-width chrome.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    shape: Shape,
    tint: Color,
    fallback: Color,
    rim: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 5.dp,
    refraction: Float = 0.45f,
    elevation: Dp = 10.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val blurLayer = rememberGraphicsLayer()
    val lens = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassLens() else null
    }
    val position = remember { PositionHolder() }
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .onGloballyPositioned { position.value = it.positionInRoot(); position.coordinateRoot = it.findRootCoordinates() }
            .graphicsLayer()
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                if (backdrop != null && position.coordinateRoot != null && position.coordinateRoot === backdrop.coordinateRoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    backdrop.generation
                    val offset = position.value - backdrop.originInRoot
                    val radius = blurRadius.toPx()
                    val rounded = (outline as? Outline.Rounded)?.roundRect
                    val uniformCorners = rounded == null || (
                        rounded.topLeftCornerRadius == rounded.topRightCornerRadius &&
                            rounded.topLeftCornerRadius == rounded.bottomLeftCornerRadius &&
                            rounded.topLeftCornerRadius == rounded.bottomRightCornerRadius
                        )
                    blurLayer.renderEffect = if (lens != null && Build.VERSION.SDK_INT >= 33 && refraction > 0f && outline !is Outline.Generic && uniformCorners) {
                        val corner = rounded?.topLeftCornerRadius?.x ?: 0f
                        lens.effect(size.width, size.height, corner, radius, refraction, density)
                    } else {
                        BlurEffect(radius, radius, TileMode.Clamp)
                    }
                    blurLayer.record {
                        translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                    }
                    clipPath(path) {
                        drawLayer(blurLayer)
                        drawRect(tint)
                    }
                } else {
                    drawPath(path, fallback)
                }
                // A restrained top light gives the glass depth without washing out its content.
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.08f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.025f),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        0f to rim,
                        0.36f to rim.copy(alpha = rim.alpha * 0.12f),
                        0.64f to rim.copy(alpha = rim.alpha * 0.08f),
                        1f to rim.copy(alpha = rim.alpha * 0.65f),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            },
        contentAlignment = contentAlignment,
        content = content
    )
}
