package com.codexbar.android.feature.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService

@Composable
internal fun DesktopBridgeSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.desktop_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.desktop_description), style = MaterialTheme.typography.bodySmall)
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
            if (state.desktop.busy) CircularProgressIndicator()
            when (val result = state.desktop.result) {
                ValidationResult.Success -> Text(stringResource(R.string.desktop_connected))
                is ValidationResult.Failure -> Text(result.message, color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
            if (state.desktop.connected) {
                OutlinedButton(enabled = !state.desktop.busy, onClick = viewModel::refreshDesktopProviders) { Text(stringResource(R.string.desktop_refresh)) }
                Text(stringResource(R.string.desktop_services_description), style = MaterialTheme.typography.bodySmall)
                AiService.entries.filter { it in state.desktop.availableServices }.forEach { service ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(service.displayName, modifier = Modifier.weight(1f))
                        Switch(checked = service in state.desktop.services, enabled = !state.desktop.busy,
                            onCheckedChange = { viewModel.setDesktopService(service, it) })
                    }
                }
                OutlinedButton(enabled = !state.desktop.busy, onClick = viewModel::disconnectDesktopBridge) {
                    Text(stringResource(R.string.desktop_disconnect))
                }
            }
        }
    }
}
