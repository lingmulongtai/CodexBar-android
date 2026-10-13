package com.codexbar.android.feature.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AcquisitionMode

@Composable
internal fun DesktopBridgeSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showPairing by remember { mutableStateOf(false) }
    var showExceptions by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.desktop_title), style = MaterialTheme.typography.titleMedium)
            AcquisitionMode.entries.forEach { mode ->
                val title = if (mode == AcquisitionMode.PHONE) R.string.acquisition_phone else R.string.acquisition_windows
                val description = if (mode == AcquisitionMode.PHONE) R.string.acquisition_phone_description else R.string.acquisition_windows_description
                val enabled = mode == AcquisitionMode.PHONE || state.desktop.connected
                Row(Modifier.fillMaxWidth().selectable(selected = state.desktop.mode == mode,
                    enabled = enabled, role = Role.RadioButton, onClick = { viewModel.setAcquisitionMode(mode) }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = state.desktop.mode == mode, enabled = enabled, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(stringResource(R.string.desktop_description), style = MaterialTheme.typography.bodySmall)
            if (!state.desktop.connected || showPairing) {
                OutlinedTextField(value = state.desktop.pairingCode, onValueChange = viewModel::updateDesktopPairingCode,
                label = { Text(stringResource(R.string.desktop_pairing_code)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), enabled = !state.desktop.busy,
                modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !state.desktop.busy, onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val code = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
                        if (code != null && code.length <= 256 && code.startsWith("CBWIN1|")) viewModel.updateDesktopPairingCode(code)
                    }) { Text(stringResource(R.string.desktop_paste)) }
                    Button(enabled = !state.desktop.busy && state.desktop.pairingCode.isNotBlank(), onClick = viewModel::connectDesktopBridge) {
                        Text(stringResource(R.string.desktop_connect))
                    }
                }
            }
            if (state.desktop.busy) CircularProgressIndicator()
            when (val result = state.desktop.result) {
                ValidationResult.Success -> Text(stringResource(R.string.desktop_connected))
                is ValidationResult.Failure -> Text(result.message, color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
            if (state.desktop.connected) {
                Text(stringResource(R.string.desktop_endpoint, state.desktop.host), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.desktop_network_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !state.desktop.busy, onClick = viewModel::refreshDesktopProviders) { Text(stringResource(R.string.desktop_refresh)) }
                if (state.desktop.mode == AcquisitionMode.WINDOWS) {
                    Text(stringResource(R.string.desktop_selected_services, state.desktop.services.joinToString { it.displayName }
                    .ifBlank { stringResource(R.string.desktop_no_services) }), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showExceptions = !showExceptions }) { Text(stringResource(R.string.desktop_exceptions)) }
                    if (showExceptions) {
                        Text(stringResource(R.string.desktop_services_description), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::clearPhoneExceptions,
                            enabled = !state.desktop.busy && state.desktop.phoneExceptions.isNotEmpty()) {
                            Text(stringResource(R.string.desktop_all_windows))
                        }
                        AiService.entries.forEach { service ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(service.displayName)
                                    Text(stringResource(if (service in state.desktop.phoneExceptions) R.string.acquisition_phone else
                                    if (service in state.desktop.availableServices) R.string.acquisition_windows else R.string.desktop_not_advertised),
                                    style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = service in state.desktop.phoneExceptions, enabled = !state.desktop.busy,
                                onCheckedChange = { viewModel.setDesktopService(service, !it) })
                            }
                        }
                    }
                }
                TextButton(onClick = { showPairing = !showPairing }) { Text(stringResource(R.string.desktop_replace_pairing)) }
                OutlinedButton(enabled = !state.desktop.busy, onClick = viewModel::disconnectDesktopBridge) {
                    Text(stringResource(R.string.desktop_disconnect))
                }
            }
        }
    }
}
