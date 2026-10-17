package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.core.presentation.QuotaPaceGuide
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/**
 * The remaining-quota bar in each design's form: Expressive leaves a gap before the track and ends
 * it with a stop dot, Liquid Glass draws a slim bar on a neutral track and Instrument a thin line.
 * Pace guide ticks mark where the remaining quota should be now, in an hour and by end of day.
 */
@Composable
internal fun QuotaMeter(progress: Float, color: Color, guide: QuotaPaceGuide?, modifier: Modifier = Modifier) {
    val style = LocalCodexBarThemeProfile.current.style
    val colors = MaterialTheme.colorScheme
    val height = when (style) {
        AppThemeStyle.MATERIAL_3 -> 8.dp
        AppThemeStyle.LIQUID_GLASS -> 6.dp
        AppThemeStyle.INSTRUMENT -> 4.dp
    }
    val track = when (style) {
        AppThemeStyle.MATERIAL_3 -> color.copy(alpha = 0.18f)
        AppThemeStyle.LIQUID_GLASS -> colors.onSurface.copy(alpha = 0.08f)
        AppThemeStyle.INSTRUMENT -> colors.surfaceContainerHighest
    }
    val marker = colors.onSurface
    val remaining = progress.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(height).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(remaining, 0f..1f)
    }) {
        val corner = CornerRadius(size.height / 2)
        if (style == AppThemeStyle.MATERIAL_3) {
            val gap = 3.dp.toPx()
            val fillEnd = size.width * remaining
            if (remaining > 0f) {
                drawRoundRect(color, size = Size((fillEnd - gap / 2).coerceAtLeast(size.height), size.height), cornerRadius = corner)
            }
            if (remaining < 1f) {
                val trackStart = if (remaining > 0f) fillEnd + gap / 2 else 0f
                drawRoundRect(
                    track,
                    topLeft = Offset(trackStart, 0f),
                    size = Size(size.width - trackStart, size.height),
                    cornerRadius = corner
                )
                drawCircle(color, radius = size.height / 4, center = Offset(size.width - size.height / 2, size.height / 2))
            }
        } else {
            drawRoundRect(track, cornerRadius = corner)
            if (remaining > 0f) drawRoundRect(color, size = Size(size.width * remaining, size.height), cornerRadius = corner)
        }
        guide?.let {
            listOf(it.todayRemaining to 0.35f, it.oneHourRemaining to 0.6f, it.nowRemaining to 1f).forEach { (fraction, alpha) ->
                if (fraction != null && fraction in 0.01f..0.99f) {
                    val x = fraction * size.width
                    drawLine(marker.copy(alpha = alpha), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}
