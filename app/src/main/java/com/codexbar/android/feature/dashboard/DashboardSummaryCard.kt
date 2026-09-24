package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaSeverity
import com.codexbar.android.ui.theme.CodexBarStateColors
import com.codexbar.android.ui.theme.providerVisualStyle

@Composable
fun DashboardSummaryCard(
    summary: DashboardSummary,
    onProviderClick: (AiService) -> Unit,
    modifier: Modifier = Modifier
) {
    val tightest = summary.tightest
    val accent = tightest?.let { providerVisualStyle(it.service).accent }
    val color = CodexBarStateColors.severityColor(tightest?.severity ?: QuotaSeverity.Unknown, accent)
    Surface(
        onClick = { tightest?.let { onProviderClick(it.service) } },
        enabled = tightest != null,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_summary_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tightest?.let { stringResource(R.string.dashboard_summary_window,
                    it.service.displayName, it.metricLabel) }
                    ?: stringResource(R.string.dashboard_summary_no_reading),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            tightest?.let {
                Text(it.remainingLabel, style = MaterialTheme.typography.titleLarge, color = color)
            }
        }
    }
}
