package com.codexbar.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import com.codexbar.android.ui.components.CodexBarFilterChip as FilterChip
import com.codexbar.android.ui.components.CodexBarTextButton as TextButton
import com.codexbar.android.ui.components.CodexBarButton as Button
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codexbar.android.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopRemoteSettingsDialog(state: DesktopConnectionState, onLoad: () -> Unit, onSave: (Long?, String?) -> Unit, onDismiss: () -> Unit) {
    val settings = state.remoteSettings
    var interval by rememberSaveable(state.remoteOwner, settings?.revision) { mutableStateOf(settings?.refreshIntervalSecs?.toString().orEmpty()) }
    var period by rememberSaveable(state.remoteOwner, settings?.revision) { mutableStateOf(settings?.reportingPeriod.orEmpty()) }
    val value = interval.toLongOrNull()
    val validInterval = value == 0L || value?.let { it in 60..86400 } == true
    val changedInterval = value != settings?.refreshIntervalSecs
    val changedPeriod = period != settings?.reportingPeriod
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.desktop_remote_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.desktop_remote_help), style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.remoteMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            (state.result as? ValidationResult.Failure)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            if (settings != null) {
                if (!settings.allowed) Text(stringResource(R.string.desktop_remote_disabled), color = MaterialTheme.colorScheme.error)
                Text(stringResource(if (settings.adaptiveRefresh) R.string.desktop_remote_adaptive else R.string.desktop_remote_fixed), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = interval, onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.desktop_remote_interval)) }, singleLine = true,
                    supportingText = { Text(stringResource(R.string.desktop_remote_interval_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !validInterval,
                    enabled = settings.allowed && !state.busy, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.desktop_remote_period), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("rolling:7" to R.string.analytics_7_days, "rolling:30" to R.string.analytics_30_days,
                        "month-to-date" to R.string.local_usage_month_to_date, "all" to R.string.local_usage_all).forEach { (id, label) ->
                        FilterChip(selected = period == id, enabled = settings.allowed && !state.busy, onClick = { period = id }, label = { Text(stringResource(label)) })
                    }
                }
            }
        }
    }, confirmButton = {
        Button(enabled = settings?.allowed == true && !state.busy && validInterval && (changedInterval || changedPeriod),
            onClick = { onSave(value.takeIf { changedInterval }, period.takeIf { changedPeriod }) }) { Text(stringResource(R.string.action_save_changes)) }
    }, dismissButton = {
        Row {
            TextButton(enabled = !state.busy, onClick = onLoad) { Text(stringResource(R.string.action_refresh)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.desktop_remote_close)) }
        }
    })
}
