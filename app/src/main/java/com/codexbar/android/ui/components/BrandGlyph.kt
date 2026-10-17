package com.codexbar.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppThemeStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The CodexBar mark of each design, drawn in the 72-unit visible area of the matching adaptive
 * launcher icon: a gauge "C", three glass quota bars, or a prompt whose cursor is a meter.
 */
@Composable
fun BrandGlyph(style: AppThemeStyle, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(40.dp)) {
        scale(size.minDimension / 72f, pivot = Offset.Zero) {
            when (style) {
                AppThemeStyle.MATERIAL_3 -> drawGaugeGlyph(primary)
                AppThemeStyle.LIQUID_GLASS -> drawGlassBarsGlyph()
                AppThemeStyle.INSTRUMENT -> drawPromptGlyph()
            }
        }
    }
}

// Every glyph is drawn in the 72-unit space; DrawScope.size stays in pixels while scaled.
private val GlyphSize = Size(72f, 72f)

private fun DrawScope.drawGaugeGlyph(container: Color) {
    drawPath(cookiePath(GlyphSize), container)
    val arc = Stroke(width = 9f, cap = StrokeCap.Round)
    val topLeft = Offset(14f, 14f)
    val arcSize = Size(44f, 44f)
    drawArc(Color.White.copy(alpha = 0.3f), 45f, 270f, false, topLeft, arcSize, style = arc)
    drawArc(Color.White, 139.5f, 175.5f, false, topLeft, arcSize, style = arc)
    drawCircle(Color(0xFFFFC56B), radius = 4.6f, center = Offset(58.5f, 36f))
}

private fun DrawScope.drawGlassBarsGlyph() {
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF3D7BFA), Color(0xFF7B5CF6)),
            start = Offset.Zero,
            end = Offset(72f, 72f)
        ),
        size = GlyphSize,
        cornerRadius = CornerRadius(17f)
    )
    listOf(17f to 32f, 31f to 22f, 45f to 12f).forEach { (x, fill) ->
        drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(x, 16f), Size(10f, 40f), CornerRadius(5f))
        drawRoundRect(Color.White, Offset(x, 56f - fill), Size(10f, fill), CornerRadius(5f))
    }
}

private fun DrawScope.drawPromptGlyph() {
    drawRoundRect(Color(0xFF14161B), size = GlyphSize, cornerRadius = CornerRadius(17f))
    drawRoundRect(
        Color(0xFF2A2E37),
        topLeft = Offset(1f, 1f),
        size = Size(70f, 70f),
        cornerRadius = CornerRadius(16f),
        style = Stroke(width = 2f)
    )
    val chevron = Path().apply {
        moveTo(15f, 20f)
        lineTo(29f, 34f)
        lineTo(15f, 48f)
    }
    drawPath(chevron, Color(0xFF5EE3A1), style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawRoundRect(Color(0xFFECEEF2).copy(alpha = 0.2f), Offset(35f, 42f), Size(24f, 9f), CornerRadius(4.5f))
    drawRoundRect(Color(0xFFECEEF2), Offset(35f, 42f), Size(15f, 9f), CornerRadius(4.5f))
}

/** Material 3 Expressive's nine-scallop "cookie" shape. */
fun cookiePath(size: Size): Path {
    val path = Path()
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f * 0.93f
    val steps = 72
    for (step in 0 until steps) {
        val t = 2.0 * PI * step / steps
        val r = radius * (1.0 + 0.07 * cos(9.0 * t))
        val x = center.x + (r * cos(t - PI / 2)).toFloat()
        val y = center.y + (r * sin(t - PI / 2)).toFloat()
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

val CookieShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(cookiePath(size))
    }
}
