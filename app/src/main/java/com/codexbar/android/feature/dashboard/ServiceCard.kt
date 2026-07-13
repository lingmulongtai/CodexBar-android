package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.presentation.ServiceQuotaStatus

@Composable
fun ServiceCard(
    service: ServiceQuotaPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Icon + Name + Tier badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = service.service.displayName,
                    modifier = Modifier.size(32.dp),
                    tint = Color(service.service.brandColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = service.service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                service.tier?.let { tier ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = tier,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Error state
            if (service.status != ServiceQuotaStatus.Fresh && service.status != ServiceQuotaStatus.Redacted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = service.freshness.staleReason ?: service.status.toLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary gauge (first window)
            service.primaryMetric?.let { metric ->
                QuotaGaugeBar(
                    metric = metric
                )
            }

            // Secondary windows
            val secondaryWindows = service.metrics.filterNot { it.id == service.primaryMetric?.id }

            if (secondaryWindows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    secondaryWindows.forEach { metric ->
                        QuotaGaugeBar(
                            metric = metric
                        )
                    }
                }
            }

            // Extra usage (Claude credits)
            service.extraUsage?.let { extra ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${extra.label}: ${extra.usedCreditsLabel} / ${extra.limitLabel} (${extra.remainingLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = service.freshness.ageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ServiceQuotaStatus.toLabel(): String {
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
