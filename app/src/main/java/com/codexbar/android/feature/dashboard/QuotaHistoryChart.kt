package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.presentation.QuotaHistoryPointPresentation
import com.codexbar.android.core.presentation.QuotaHistoryPresentation
import com.codexbar.android.ui.theme.CodexBarSpacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun QuotaHistoryChart(
    history: QuotaHistoryPresentation,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val points = history.points
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }
    val firstLabel = points.firstOrNull()?.capturedAt?.let(formatter::format).orEmpty()
    val lastLabel = points.lastOrNull()?.capturedAt?.let(formatter::format).orEmpty()
    val chartDescription = if (points.size >= 2) {
        stringResource(R.string.quota_history_accessibility, firstLabel, lastLabel)
    } else {
        stringResource(R.string.quota_history_collecting)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quota_history_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                if (points.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.quota_history_sample_count,
                            points.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (points.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.quota_history_collecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                HistoryCanvas(
                    points = points,
                    accent = accent,
                    showGrid = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .semantics { contentDescription = chartDescription }
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = firstLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lastLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuotaHistorySparkline(
    history: QuotaHistoryPresentation,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (history.points.size < 2) return
    HistoryCanvas(
        points = history.points,
        accent = accent,
        showGrid = false,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    )
}

@Composable
private fun HistoryCanvas(
    points: List<QuotaHistoryPointPresentation>,
    accent: Color,
    showGrid: Boolean,
    modifier: Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    val resetColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.44f)
    Canvas(modifier = modifier) {
        if (showGrid) {
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = size.height * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        drawHistoryLines(points, accent, resetColor)
    }
}

private fun DrawScope.drawHistoryLines(
    points: List<QuotaHistoryPointPresentation>,
    accent: Color,
    resetColor: Color
) {
    if (points.isEmpty()) return
    val firstTime = points.first().capturedAt.toEpochMilli()
    val span = (points.last().capturedAt.toEpochMilli() - firstTime).coerceAtLeast(1L)
    fun offset(point: QuotaHistoryPointPresentation): Offset {
        val x = ((point.capturedAt.toEpochMilli() - firstTime).toDouble() / span.toDouble())
            .toFloat() * size.width
        val y = size.height - point.usedFraction.coerceIn(0f, 1f) * size.height
        return Offset(x, y)
    }

    var path = Path()
    points.forEachIndexed { index, point ->
        val pointOffset = offset(point)
        if (index == 0 || point.startsNewCycle) {
            if (index > 0) {
                drawPath(
                    path = path,
                    color = accent,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawLine(
                    color = resetColor,
                    start = Offset(pointOffset.x, 0f),
                    end = Offset(pointOffset.x, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                )
                path = Path()
            }
            path.moveTo(pointOffset.x, pointOffset.y)
        } else {
            path.lineTo(pointOffset.x, pointOffset.y)
        }
    }
    drawPath(
        path = path,
        color = accent,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )
    val latest = offset(points.last())
    drawCircle(color = accent, radius = 4.dp.toPx(), center = latest)
    drawCircle(color = Color.White, radius = 1.7.dp.toPx(), center = latest)
}
