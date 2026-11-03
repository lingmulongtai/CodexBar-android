package com.codexbar.android.ui.components

import android.os.Build
import com.codexbar.android.core.domain.model.GlassSurfaceGroup
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

fun Modifier.glassBackdropSource(backdrop: GlassBackdrop, behind: GlassBackdrop? = null): Modifier = this
    .onGloballyPositioned { backdrop.originInRoot = it.positionInRoot(); backdrop.coordinateRoot = it.findRootCoordinates() }
    // Keep the source's display list when only a glass consumer needs redrawing. Without this
    // boundary, its generation write and the consumer's invalidation keep redrawing each other.
    .graphicsLayer()
    .drawWithContent {
        backdrop.layer.record {
            if (behind != null && behind.coordinateRoot != null && behind.coordinateRoot === backdrop.coordinateRoot) {
                behind.generation
                val offset = behind.originInRoot - backdrop.originInRoot
                translate(offset.x, offset.y) { drawLayer(behind.layer) }
            }
            this@drawWithContent.drawContent()
        }
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
    dispersion: Float = 0.4f,
    allowAsymmetricCorners: Boolean = false,
    bendReachScale: Float = 1f,
    shaderRimShine: Float = 0.14f,
    saturation: Float = 1.12f,
    elevation: Dp = 10.dp,
    contentAlignment: Alignment = Alignment.Center,
    tuningGroup: GlassSurfaceGroup = GlassSurfaceGroup.CONTROLS,
    content: @Composable BoxScope.() -> Unit
) {
    val tuning = LocalDeveloperSettings.current.tuning(tuningGroup)
    val material = tuning.resolve(GlassMaterial(blurRadius.value, refraction, dispersion, rim.alpha,
        shaderRimShine, saturation, tint.alpha, bendReachScale))
    val effectiveRim = rim.copy(alpha = material.rim)
    val effectiveTint = tint.copy(alpha = material.tint)
    val effectiveFallback = fallback.copy(alpha = tuning.tint ?: fallback.alpha)
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
                    val radius = material.frost.dp.toPx()
                    val rounded = (outline as? Outline.Rounded)?.roundRect
                    val circularCorners = rounded == null || (
                        rounded.topLeftCornerRadius.x == rounded.topLeftCornerRadius.y &&
                            rounded.topRightCornerRadius.x == rounded.topRightCornerRadius.y &&
                            rounded.bottomRightCornerRadius.x == rounded.bottomRightCornerRadius.y &&
                            rounded.bottomLeftCornerRadius.x == rounded.bottomLeftCornerRadius.y
                        )
                    val uniformCorners = rounded == null || (
                        rounded.topLeftCornerRadius == rounded.topRightCornerRadius &&
                            rounded.topLeftCornerRadius == rounded.bottomRightCornerRadius &&
                            rounded.topLeftCornerRadius == rounded.bottomLeftCornerRadius
                        )
                    blurLayer.renderEffect = if (lens != null && Build.VERSION.SDK_INT >= 33 && material.bend > 0f && outline !is Outline.Generic && circularCorners && (uniformCorners || allowAsymmetricCorners)) {
                        lens.effect(size.width, size.height,
                            rounded?.topLeftCornerRadius?.x ?: 0f, rounded?.topRightCornerRadius?.x ?: 0f,
                            rounded?.bottomRightCornerRadius?.x ?: 0f, rounded?.bottomLeftCornerRadius?.x ?: 0f,
                            radius, material.bend * material.reach, material.dispersion, material.shine, material.saturation, density)
                    } else if (radius > 0f) {
                        BlurEffect(radius, radius, TileMode.Clamp)
                    } else null
                    blurLayer.record {
                        translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                    }
                    clipPath(path) {
                        drawLayer(blurLayer)
                        drawRect(effectiveTint)
                    }
                } else {
                    drawPath(path, effectiveFallback)
                }
                // Keep the highlight in the existing rim draw, without another gradient layer.
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        0f to effectiveRim,
                        0.36f to effectiveRim.copy(alpha = effectiveRim.alpha * 0.22f),
                        0.64f to effectiveRim.copy(alpha = effectiveRim.alpha * 0.12f),
                        1f to effectiveRim.copy(alpha = effectiveRim.alpha * 0.65f),
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
