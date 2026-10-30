package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.codexbar.android.ui.components.GlassCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.core.presentation.QuotaSeverity
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus
import com.codexbar.android.ui.components.ProviderIcon
import com.codexbar.android.ui.theme.CodexBarThemeProfile
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import com.codexbar.android.ui.theme.PlexMonoFamily
import com.codexbar.android.ui.theme.providerVisualStyle
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The dashboard's opening reading: the quota window that runs out first, in each design's form. */
@Composable
fun DashboardHero(
    summary: DashboardSummary,
    onProviderClick: (AiService) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = LocalCodexBarThemeProfile.current
    val tightest = summary.tightest
    if (tightest == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = profile.cardColor,
            border = BorderStroke(1.dp, profile.cardBorderColor)
        ) {
            Text(
                stringResource(R.string.dashboard_summary_no_reading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
        return
    }
    val onClick = { onProviderClick(tightest.service) }
    when (profile.style) {
        AppThemeStyle.MATERIAL_3 -> ExpressiveHero(tightest, onClick, modifier)
        AppThemeStyle.LIQUID_GLASS -> GlassHero(tightest, onClick, modifier)
    }
}

@Composable
internal fun severityTone(severity: QuotaSeverity, profile: CodexBarThemeProfile, neutral: Color): Color {
    return when (severity) {
        QuotaSeverity.Critical -> profile.criticalColor
        QuotaSeverity.Warning -> profile.warningColor
        QuotaSeverity.Good -> profile.goodColor ?: neutral
        QuotaSeverity.Unknown,
        QuotaSeverity.Redacted -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun ExpressiveHero(tightest: TightestQuota, onClick: () -> Unit, modifier: Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = profile.heroContainerColor,
        contentColor = profile.onHeroContainerColor
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_summary_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                ProviderChip(
                    tightest = tightest,
                    container = profile.onHeroContainerColor.copy(alpha = 0.08f)
                )
            }
            BigReading(tightest, color = profile.onHeroContainerColor, size = 76.sp)
            WavyMeter(
                fraction = (tightest.remainingFraction ?: 0.0).toFloat(),
                color = MaterialTheme.colorScheme.primary
            )
            tightest.resetLabel?.let { reset ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(reset, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            tightest.paceLabel?.let { pace ->
                Surface(
                    shape = CircleShape,
                    color = severityTone(tightest.severity, profile, profile.onHeroContainerColor).copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(pace, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** A single, legible reading; each provider keeps its own readings in the list below. */
@Composable
private fun GlassHero(tightest: TightestQuota, onClick: () -> Unit, modifier: Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val tone = severityTone(tightest.severity, profile, colors.onSurface)
    val accent = providerVisualStyle(tightest.service).accent
    GlassCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(
                accent.copy(alpha = if (profile.isDark) 0.17f else 0.10f),
                Color.Transparent
            ))).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderBadge(tightest.service, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.dashboard_summary_title), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.dashboard_summary_window, tightest.service.displayName, tightest.metricLabel),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            BigReading(tightest, color = tone, size = 54.sp)
            QuotaMeter((tightest.remainingFraction ?: 0.0).toFloat(), tone, null)
            tightest.resetLabel?.let { reset ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                    Text(reset, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** "29%" in the design's numeral face, with "left" beside it; falls back to the full label. */
@Composable
private fun BigReading(tightest: TightestQuota, color: Color, size: TextUnit, modifier: Modifier = Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    val percent = tightest.remainingPercent
    val description = stringResource(R.string.dashboard_summary_window, tightest.service.displayName, tightest.remainingLabel)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (percent == null) {
            Text(tightest.remainingLabel, style = MaterialTheme.typography.headlineMedium, color = color)
            return@Row
        }
        Text(
            buildAnnotatedString {
                append(percent.toString())
                withStyle(SpanStyle(fontSize = size * 0.45f)) { append("%") }
            },
            color = color,
            fontFamily = profile.numeralFamily,
            fontSize = size,
            lineHeight = size,
            fontWeight = if (profile.style == AppThemeStyle.LIQUID_GLASS) FontWeight.SemiBold else FontWeight.ExtraBold,
            letterSpacing = (-1.5).sp
        )
        run {
            Text(
                stringResource(R.string.dashboard_hero_left),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.padding(bottom = (size.value * 0.12f).dp)
            )
        }
    }
}

@Composable
private fun ProviderChip(tightest: TightestQuota, container: Color, mono: Boolean = false) {
    Surface(shape = if (mono) RoundedCornerShape(8.dp) else CircleShape, color = container) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProviderIcon(tightest.service, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.dashboard_summary_window, tightest.service.displayName, tightest.metricLabel),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = if (mono) PlexMonoFamily else MaterialTheme.typography.labelLarge.fontFamily,
                fontWeight = if (mono) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Material 3 Expressive's wavy progress: a wave for what remains, a gap, a flat track, a stop. */
@Composable
private fun WavyMeter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val remaining = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(remaining, 0f..1f) }
    ) {
        val stroke = 4.dp.toPx()
        val amplitude = 3.dp.toPx()
        val wavelength = 20.dp.toPx()
        val gap = 6.dp.toPx()
        val middle = size.height / 2
        val waveEnd = size.width * remaining
        if (remaining > 0f) {
            val wave = Path()
            var x = stroke / 2
            wave.moveTo(x, middle)
            while (x < waveEnd - stroke / 2) {
                x = (x + 1.5f).coerceAtMost(waveEnd - stroke / 2)
                wave.lineTo(x, middle + amplitude * sin(2 * PI * x / wavelength).toFloat())
            }
            drawPath(wave, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        val trackStart = if (remaining > 0f) waveEnd + gap else stroke / 2
        val trackEnd = size.width - stroke * 1.5f
        if (trackStart < trackEnd) {
            drawLine(color.copy(alpha = 0.22f), Offset(trackStart, middle), Offset(trackEnd, middle), stroke, StrokeCap.Round)
        }
        drawCircle(color, radius = stroke / 2, center = Offset(size.width - stroke / 2, middle))
    }
}
