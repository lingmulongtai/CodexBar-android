package com.codexbar.android.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
    internal var originInRoot: Offset = Offset.Zero
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.originInRoot = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

private class PositionHolder {
    var value: Offset = Offset.Zero
}

/**
 * A glass panel: the recorded backdrop, blurred and tinted, clipped to [shape], with a light rim
 * that is brighter along the top edge. Android 11 and older cannot blur render nodes, so they get
 * an opaque [fallback] that keeps text readable.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    shape: Shape,
    tint: Color,
    fallback: Color,
    rim: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 22.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val blurLayer = rememberGraphicsLayer()
    val position = remember { PositionHolder() }
    Box(
        modifier = modifier
            .onGloballyPositioned { position.value = it.positionInRoot() }
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val offset = position.value - backdrop.originInRoot
                    val radius = blurRadius.toPx()
                    blurLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
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
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(listOf(rim, rim.copy(alpha = rim.alpha * 0.3f))),
                    style = Stroke(width = 1.dp.toPx())
                )
            },
        contentAlignment = contentAlignment,
        content = content
    )
}
