package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.QuotaResetActivity
import com.codexbar.android.core.domain.model.QuotaResetKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun ResetHistoryCard(activities: List<QuotaResetActivity>, onClear: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val locale = LocalContext.current.resources.configuration.locales[0]
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale).withZone(ZoneId.systemDefault())
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.reset_history_clear)) },
            text = { Text(stringResource(R.string.reset_history_clear_confirm)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) {
                Text(stringResource(R.string.reset_history_clear))
            } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) {
                Text(stringResource(R.string.action_cancel))
            } })
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reset_history_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                if (activities.isNotEmpty()) TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.reset_history_clear))
                }
            }
            Text(stringResource(R.string.reset_history_description), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (activities.isEmpty()) Text(stringResource(R.string.reset_history_empty),
                style = MaterialTheme.typography.bodyMedium)
            activities.take(if (expanded) 100 else 10).forEach { activity ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val label = when (activity.kind) {
                        QuotaResetKind.WINDOW_RESET -> stringResource(R.string.reset_history_window, activity.windowLabel.orEmpty())
                        QuotaResetKind.CREDITS_ADDED -> stringResource(R.string.reset_history_credits_added,
                            activity.count, activity.availableCount ?: 0)
                        QuotaResetKind.CREDITS_DECREASED -> stringResource(R.string.reset_history_credits_decreased,
                            activity.count, activity.availableCount ?: 0)
                        QuotaResetKind.CREDITS_EXPIRING -> stringResource(R.string.notification_reset_credits_expiring)
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.reset_history_observed_at,
                        formatter.format(Instant.ofEpochMilli(activity.observedAtEpochMillis))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    activity.boundaryEpochSeconds?.let { boundary ->
                        Text(stringResource(if (activity.kind == QuotaResetKind.CREDITS_EXPIRING)
                            R.string.reset_history_expires_at else R.string.reset_history_boundary,
                            formatter.format(Instant.ofEpochSecond(boundary))),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (activities.size > 10) TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.reset_history_show_less else R.string.reset_history_show_all))
            }
        }
    }
}
