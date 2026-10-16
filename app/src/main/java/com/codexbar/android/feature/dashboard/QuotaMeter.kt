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
import com.codexbar.android.core.presentation.QuotaPaceGuide

@Composable
internal fun QuotaMeter(progress: Float, color: Color, guide: QuotaPaceGuide?, modifier: Modifier = Modifier) {
    val track = color.copy(alpha = 0.14f)
    val marker = MaterialTheme.colorScheme.onSurface
    val remaining = progress.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(10.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(remaining, 0f..1f)
    }) {
        val corner = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = corner)
        if (remaining > 0f) drawRoundRect(color, size = Size(size.width * remaining, size.height), cornerRadius = corner)
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
