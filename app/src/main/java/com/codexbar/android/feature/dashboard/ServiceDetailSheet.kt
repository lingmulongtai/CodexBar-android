package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.presentation.ExtraUsagePresentation
import com.codexbar.android.core.presentation.QuotaMetricPresentation
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus
import com.codexbar.android.ui.theme.CodexBarSpacing
import com.codexbar.android.ui.theme.CodexBarStateColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailSheet(
    service: ServiceQuotaPresentation,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CodexBarSpacing.large)
                .padding(bottom = CodexBarSpacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.large)
        ) {
            ServiceDetailHeader(service)
            ServiceStateSummary(service)

            if (service.metrics.isNotEmpty()) {
                SectionTitle("Quota windows")
                Column(verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)) {
                    service.metrics.forEach { metric ->
                        MetricDetailCard(metric)
                    }
                }
            }

            service.extraUsage?.let { extraUsage ->
                SectionTitle("Extra usage")
                ExtraUsageCard(extraUsage)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Refresh")
                }
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun ServiceDetailHeader(service: ServiceQuotaPresentation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = service.service.displayName,
            modifier = Modifier.size(40.dp),
            tint = CodexBarStateColors.providerAccent(service.service)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = service.service.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOfNotNull(service.tier, service.accountLabel).joinToString(" - ")
                    .ifBlank { "Quota monitor" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val statusColor = service.status.statusColor()
        Surface(
            shape = MaterialTheme.shapes.small,
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = service.status.toDetailLabel(),
                modifier = Modifier.padding(horizontal = CodexBarSpacing.small, vertical = CodexBarSpacing.xsmall),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ServiceStateSummary(service: ServiceQuotaPresentation) {
    val statusColor = service.status.statusColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.large),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            DetailRow("Status", service.status.toDetailLabel(), statusColor)
            DetailRow("Freshness", service.freshness.ageLabel)
            service.freshness.staleReason?.let { DetailRow("Reason", it, statusColor) }
            service.freshness.nextRetryAt?.let { DetailRow("Next retry", it.toString()) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricDetailCard(metric: QuotaMetricPresentation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.large),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium)
            ) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = metric.remainingLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = CodexBarStateColors.severityColor(metric.severity),
                    fontWeight = FontWeight.SemiBold
                )
            }

            QuotaGaugeBar(metric = metric)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.small),
                verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
            ) {
                DetailChip(metric.usedLabel)
                DetailChip(metric.remainingLabel)
                metric.resetLabel?.let { DetailChip(it) }
                metric.pace.reserveLabel?.let { DetailChip(it) }
            }

            if (metric.pace.label.isNotBlank()) {
                DetailRow("Pace", metric.pace.label)
            }
            metric.pace.forecastLabel?.let { DetailRow("Forecast", it) }
            metric.resetsAt?.let { DetailRow("Reset time", it.toString()) }
        }
    }
}

@Composable
private fun ExtraUsageCard(extraUsage: ExtraUsagePresentation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(CodexBarSpacing.large),
            verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.small)
        ) {
            DetailRow(extraUsage.label, extraUsage.usedCreditsLabel)
            DetailRow("Limit", extraUsage.limitLabel)
            DetailRow("Remaining", extraUsage.remainingLabel, CodexBarStateColors.severityColor(extraUsage.severity))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.66f)
        )
    }
}

@Composable
private fun DetailChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = CodexBarSpacing.small, vertical = CodexBarSpacing.xsmall),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun ServiceQuotaStatus.toDetailLabel(): String {
    return when (this) {
        ServiceQuotaStatus.Fresh -> "Fresh"
        ServiceQuotaStatus.Stale -> "Stale data"
        ServiceQuotaStatus.Loading -> "Loading"
        ServiceQuotaStatus.AuthRequired -> "Re-authentication required"
        ServiceQuotaStatus.RateLimited -> "Rate limited"
        ServiceQuotaStatus.Offline -> "Offline"
        ServiceQuotaStatus.ProviderError -> "Provider error"
        ServiceQuotaStatus.Disconnected -> "Not connected"
        ServiceQuotaStatus.Redacted -> "Quota hidden"
    }
}

@Composable
private fun ServiceQuotaStatus.statusColor(): Color {
    return when (this) {
        ServiceQuotaStatus.Fresh -> MaterialTheme.colorScheme.primary
        ServiceQuotaStatus.Redacted -> MaterialTheme.colorScheme.outline
        ServiceQuotaStatus.Stale,
        ServiceQuotaStatus.Loading,
        ServiceQuotaStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        ServiceQuotaStatus.AuthRequired,
        ServiceQuotaStatus.RateLimited,
        ServiceQuotaStatus.Offline,
        ServiceQuotaStatus.ProviderError -> MaterialTheme.colorScheme.error
    }
}
