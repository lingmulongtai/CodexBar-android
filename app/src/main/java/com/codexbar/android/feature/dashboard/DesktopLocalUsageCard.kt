package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.DesktopLocalUsage
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

@Composable
internal fun DesktopLocalUsageCard(usage: DesktopLocalUsage) {
    val numbers = NumberFormat.getIntegerInstance()
    val money = NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance("USD") }
    val updated = Instant.ofEpochSecond(usage.updatedAtEpochSeconds)
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.local_usage_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.local_usage_description), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.local_usage_today, usage.todayDate), style = MaterialTheme.typography.labelMedium)
            usage.todayTokens?.let { LocalTotalRow(R.string.local_usage_today_tokens, numbers.format(it)) }
            usage.todayCostUsd?.let { LocalTotalRow(R.string.local_usage_today_cost, money.format(it)) }
            usage.thirtyDayTokens?.let { LocalTotalRow(R.string.local_usage_thirty_day_tokens, numbers.format(it)) }
            usage.thirtyDayCostUsd?.let { LocalTotalRow(R.string.local_usage_thirty_day_cost, money.format(it)) }
            usage.reportingPeriod?.let { period ->
                val label = when (period) {
                    "all" -> stringResource(R.string.local_usage_all)
                    "month-to-date" -> stringResource(R.string.local_usage_month_to_date)
                    else -> stringResource(R.string.local_usage_rolling_days, period.removePrefix("rolling:").toInt())
                }
                LocalTotalRow(R.string.local_usage_period, label)
            }
            usage.periodTokens?.let { LocalTotalRow(R.string.local_usage_period_tokens, numbers.format(it)) }
            usage.periodCostUsd?.let { LocalTotalRow(R.string.local_usage_period_cost, money.format(it)) }
            usage.topModel?.let { LocalTotalRow(R.string.local_usage_top_model, it) }
            usage.incompleteRequestCount?.let { LocalTotalRow(R.string.local_usage_incomplete, numbers.format(it)) }
            Text(stringResource(R.string.local_usage_updated, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault()).format(updated)), style = MaterialTheme.typography.labelSmall)
            if (Instant.now().epochSecond - usage.updatedAtEpochSeconds > 35 * 60) {
                Text(stringResource(R.string.local_usage_stale), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LocalTotalRow(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
