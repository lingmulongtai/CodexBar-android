package com.codexbar.android.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.codexbar.android.ui.components.CodexBarCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.codexbar.android.ui.components.CodexBarOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.codexbar.android.ui.components.GlassCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.core.presentation.QuotaMetricPresentation
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus
import com.codexbar.android.core.presentation.compactModelQuotaLabel
import com.codexbar.android.ui.components.CookieShape
import com.codexbar.android.ui.components.ProviderIcon
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import com.codexbar.android.ui.theme.PlexMonoFamily
import com.codexbar.android.ui.theme.providerVisualStyle

/** Where a card sits in a single-column list, so neighbouring cards can read as one group. */
enum class CardGrouping { Alone, First, Middle, Last }

/** A glanceable overview; history, telemetry and reset plans live in the detail sheet. */
@Composable
fun ServiceCard(
    service: ServiceQuotaPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    refreshing: Boolean = false,
    grouping: CardGrouping = CardGrouping.Alone,
    expanded: Boolean = false,
    onOpenDetails: () -> Unit = onClick,
    onManageConnection: () -> Unit = {}
) {
    val selectedModifier = modifier.fillMaxWidth().semantics { this.selected = selected }
    when (LocalCodexBarThemeProfile.current.style) {
        AppThemeStyle.MATERIAL_3 -> ExpressiveServiceCard(service, onClick, selected, refreshing, grouping, selectedModifier)
        AppThemeStyle.LIQUID_GLASS -> GlassServiceRow(service, onClick, selected, refreshing, grouping, modifier.fillMaxWidth())

    }
}

private fun CardGrouping.shape(outer: Dp, inner: Dp): Shape = when (this) {
    CardGrouping.Alone -> RoundedCornerShape(outer)
    CardGrouping.First -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    CardGrouping.Middle -> RoundedCornerShape(inner)
    CardGrouping.Last -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
}

private val CardGrouping.hasRowAbove: Boolean
    get() = this == CardGrouping.Middle || this == CardGrouping.Last

private fun ServiceQuotaPresentation.displayedMetrics(): List<QuotaMetricPresentation> {
    return (listOfNotNull(primaryMetric) + metrics.filterNot { it.id == primaryMetric?.id }).take(2)
}

private fun ServiceQuotaPresentation.metricLabel(metric: QuotaMetricPresentation): String {
    return if (service == AiService.ANTIGRAVITY) compactModelQuotaLabel(metric.label) ?: metric.label else metric.label
}

private fun QuotaMetricPresentation.percentText(): String = remainingPercent?.let { "$it%" } ?: remainingLabel

@Composable
private fun ServiceQuotaPresentation.sourceAndAge(): String {
    val source = stringResource(if (isDesktopSource) R.string.usage_source_windows else R.string.usage_source_phone)
    return "$source · ${freshness.staleReason ?: freshness.ageLabel}"
}

@Composable
private fun ServiceQuotaPresentation.statusColor(): Color {
    return if (needsAttention()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun MetricLabelRow(service: ServiceQuotaPresentation, metric: QuotaMetricPresentation, valueColor: Color, small: Boolean) {
    val reset = compactCountdown(metric.resetsAt) ?: metric.resetLabel
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            listOfNotNull(service.metricLabel(metric), reset).joinToString(" · "),
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            metric.percentText(),
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun ExpressiveServiceCard(
    service: ServiceQuotaPresentation,
    onClick: () -> Unit,
    selected: Boolean,
    refreshing: Boolean,
    grouping: CardGrouping,
    modifier: Modifier
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val accent = providerVisualStyle(service.service).accent
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = grouping.shape(outer = 28.dp, inner = 8.dp),
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = profile.cardColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderBadge(service.service, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        service.service.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(service.tier, service.sourceAndAge()).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = service.statusColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (refreshing) SmallSpinner(16.dp)
            }
            MetricColumns(service, spacing = 18.dp) { metric ->
                MetricLabelRow(service, metric, severityTone(metric.severity, profile, colors.onSurface), small = false)
                QuotaMeter(metric.barProgress, accent, metric.pace.guide)
            }
        }
    }
}

@Composable
private fun GlassServiceRow(
    service: ServiceQuotaPresentation,
    onClick: () -> Unit,
    selected: Boolean,
    refreshing: Boolean,
    grouping: CardGrouping,
    modifier: Modifier
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val accent = providerVisualStyle(service.service).accent
    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = grouping.shape(outer = 26.dp, inner = 0.dp),
        selected = selected,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null
    ) {
        Column {
            if (grouping.hasRowAbove) {
                HorizontalDivider(Modifier.padding(start = 66.dp), thickness = 0.5.dp, color = colors.outlineVariant)
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProviderBadge(service.service, 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            service.service.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (refreshing) {
                            SmallSpinner(15.dp, colors.onSurfaceVariant)
                        } else {
                            Text(
                                service.freshness.staleReason ?: service.freshness.ageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = service.statusColor(),
                                maxLines = 1
                            )
                        }
                    }
                    MetricColumns(service, spacing = 14.dp) { metric ->
                        MetricLabelRow(service, metric, severityTone(metric.severity, profile, colors.onSurface), small = true)
                        QuotaMeter(metric.barProgress, accent, metric.pace.guide)
                    }
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** The provider's mark set in the design's container: a cookie, a glass tile or a bare plate. */
@Composable
internal fun ProviderBadge(service: AiService, size: Dp) {
    when (LocalCodexBarThemeProfile.current.style) {
        AppThemeStyle.MATERIAL_3 -> Box(
            modifier = Modifier
                .size(size)
                .clip(CookieShape)
                .background(providerVisualStyle(service).accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            ProviderIcon(service, modifier = Modifier.size(size * 0.55f))
        }
        AppThemeStyle.LIQUID_GLASS -> Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(size * 0.28f),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE5E5EA))
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProviderIcon(service, modifier = Modifier.size(size * 0.6f))
            }
        }
    }
}

/** Up to two quota windows side by side; providers without windows show their plan instead. */
@Composable
private fun MetricColumns(
    service: ServiceQuotaPresentation,
    spacing: Dp,
    content: @Composable (QuotaMetricPresentation) -> Unit
) {
    val metrics = service.displayedMetrics()
    if (metrics.isEmpty()) {
        Text(
            service.tier ?: service.status.toLabel(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        metrics.forEach { metric ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                content(metric)
            }
        }
    }
}

@Composable
private fun ServiceQuotaStatus.toLabel(): String {
    return when (this) {
        ServiceQuotaStatus.Fresh -> stringResource(R.string.status_fresh)
        ServiceQuotaStatus.Stale -> stringResource(R.string.status_stale)
        ServiceQuotaStatus.Loading -> stringResource(R.string.status_loading)
        ServiceQuotaStatus.AuthRequired -> stringResource(R.string.status_reauthentication_required)
        ServiceQuotaStatus.RateLimited -> stringResource(R.string.status_rate_limited)
        ServiceQuotaStatus.Offline -> stringResource(R.string.status_offline)
        ServiceQuotaStatus.ProviderError -> stringResource(R.string.status_provider_error)
        ServiceQuotaStatus.Disconnected -> stringResource(R.string.status_not_connected)
        ServiceQuotaStatus.Redacted -> stringResource(R.string.status_quota_hidden)
    }
}
