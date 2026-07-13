package com.codexbar.android.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.presentation.QuotaMetricPresentation
import com.codexbar.android.ui.theme.CodexBarStateColors
import kotlin.math.abs

@Composable
fun QuotaRingGauge(
    metric: QuotaMetricPresentation,
    goodColor: Color,
    modifier: Modifier = Modifier
) {
    val targetProgress = metric.usedFraction?.toFloat()?.coerceIn(0f, 1f) ?: 0f
    val progress = remember(metric.id) { Animatable(targetProgress) }
    LaunchedEffect(targetProgress) {
        if (abs(progress.value - targetProgress) > 0.001f) {
            progress.animateTo(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }
    }

    val gaugeColor = CodexBarStateColors.severityColor(metric.severity, goodColor)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gaugeDescription = stringResource(
        R.string.quota_metric_description,
        metric.label,
        metric.usedLabel
    )
    val percentLabel = metric.usedPercent?.let {
        stringResource(R.string.quota_percent_value, it)
    } ?: "\u2014"

    Box(
        modifier = modifier
            .size(132.dp)
            .clearAndSetSemantics { contentDescription = gaugeDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .padding(5.dp)
        ) {
            val strokeWidth = 12.dp.toPx()
            inset(strokeWidth / 2f) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                if (progress.value > 0f) {
                    drawArc(
                        color = gaugeColor,
                        startAngle = -90f,
                        sweepAngle = progress.value * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentLabel,
                style = MaterialTheme.typography.displaySmall,
                color = gaugeColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        }
    }
}
