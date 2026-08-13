package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.presentation.CodexContextPresentation
import com.codexbar.android.core.presentation.CodexDailyTokenPresentation
import com.codexbar.android.core.presentation.CodexTelemetryPresentation
import com.codexbar.android.core.presentation.CodexTokenTotalsPresentation
import com.codexbar.android.ui.theme.CodexBarSpacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun CodexTelemetryCompactCard(
    telemetry: CodexTelemetryPresentation,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = accent
                )
                Text(
                    text = stringResource(R.string.codex_telemetry_compact_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.codex_telemetry_today_tokens,
                        telemetry.tokenUsage.today.totalLabel
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            telemetry.currentContext?.let { context ->
                ContextProgress(context = context, accent = accent, compact = true)
            }
            if (telemetry.currentContext == null) {
                Text(
                    text = stringResource(R.string.codex_telemetry_context_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodexTelemetryDetail(
    telemetry: CodexTelemetryPresentation,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
    ) {
        telemetry.currentContext?.let { context ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
            ) {
                Column(
                    modifier = Modifier.padding(CodexBarSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = accent)
                        Text(
                            text = stringResource(R.string.codex_telemetry_context_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = CodexBarSpacing.small)
                                .weight(1f)
                        )
                        Text(
                            text = stringResource(
                                R.string.codex_telemetry_context_percent,
                                context.usedPercent
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ContextProgress(context = context, accent = accent, compact = false)
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small),
            maxItemsInEachRow = 3
        ) {
            TokenPeriodCard(
                label = stringResource(R.string.codex_telemetry_period_today),
                totals = telemetry.tokenUsage.today,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            TokenPeriodCard(
                label = stringResource(R.string.codex_telemetry_period_7_days),
                totals = telemetry.tokenUsage.last7Days,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            TokenPeriodCard(
                label = stringResource(R.string.codex_telemetry_period_30_days),
                totals = telemetry.tokenUsage.last30Days,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(CodexBarSpacing.large),
                verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.QueryStats, contentDescription = null, tint = accent)
                    Text(
                        text = stringResource(R.string.codex_telemetry_daily_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = CodexBarSpacing.small)
                    )
                }
                CodexDailyTokenChart(
                    daily = telemetry.tokenUsage.daily,
                    accent = accent
                )
                TokenBreakdown(telemetry.tokenUsage.last30Days)
            }
        }

        if (telemetry.tokenUsage.models.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(CodexBarSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DataUsage, contentDescription = null, tint = accent)
                        Text(
                            text = stringResource(R.string.codex_telemetry_models_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = CodexBarSpacing.small)
                        )
                    }
                    telemetry.tokenUsage.models.forEach { model ->
                        Column(verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.xsmall)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = model.model,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.codex_telemetry_tokens_value,
                                        model.totalLabel
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { model.shareFraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = accent,
                                trackColor = accent.copy(alpha = 0.13f)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.codex_telemetry_source_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContextProgress(
    context: CodexContextPresentation,
    accent: Color,
    compact: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.xsmall)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = context.model,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.codex_telemetry_context_value,
                    context.usageLabel
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { context.usedFraction },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = accent.copy(alpha = 0.13f)
        )
        if (!compact) {
            Text(
                text = stringResource(
                    R.string.codex_telemetry_session_tokens,
                    java.text.NumberFormat.getIntegerInstance().format(context.sessionTokens)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TokenPeriodCard(
    label: String,
    totals: CodexTokenTotalsPresentation,
    accent: Color,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.widthIn(min = 94.dp),
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.xsmall)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = totals.totalLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = stringResource(R.string.codex_telemetry_tokens_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TokenBreakdown(totals: CodexTokenTotalsPresentation) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small),
        verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
    ) {
        TelemetryDetailChip(stringResource(R.string.codex_telemetry_input_tokens, totals.inputLabel))
        TelemetryDetailChip(stringResource(R.string.codex_telemetry_cached_tokens, totals.cachedInputLabel))
        TelemetryDetailChip(stringResource(R.string.codex_telemetry_output_tokens, totals.outputLabel))
        TelemetryDetailChip(stringResource(R.string.codex_telemetry_reasoning_tokens, totals.reasoningOutputLabel))
    }
}

@Composable
private fun TelemetryDetailChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = CodexBarSpacing.small,
                vertical = CodexBarSpacing.xsmall
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun CodexDailyTokenChart(
    daily: List<CodexDailyTokenPresentation>,
    accent: Color
) {
    val visible = daily.takeLast(30)
    if (visible.isEmpty()) {
        Text(
            text = stringResource(R.string.codex_telemetry_no_history),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }
    val firstLabel = visible.first().date.format(formatter)
    val lastLabel = visible.last().date.format(formatter)
    val maxTokens = visible.maxOf(CodexDailyTokenPresentation::totalTokens).coerceAtLeast(1L)
    val description = stringResource(
        R.string.codex_telemetry_daily_accessibility,
        firstLabel,
        lastLabel,
        visible.last().totalLabel
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .semantics { contentDescription = description }
    ) {
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (visible.size - 1)) / visible.size)
            .coerceAtLeast(1.dp.toPx())
        visible.forEachIndexed { index, entry ->
            val fraction = entry.totalTokens.toFloat() / maxTokens.toFloat()
            val height = fraction.coerceIn(0f, 1f) * size.height
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height.coerceAtLeast(2.dp.toPx())),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
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
