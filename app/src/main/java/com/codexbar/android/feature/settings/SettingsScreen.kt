package com.codexbar.android.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.security.PrivacySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onScreenPrivacyChanged: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notifications
            NotificationsSection(
                enabled = uiState.notificationsEnabled,
                onToggle = { viewModel.setNotificationsEnabled(it) }
            )

            PrivacySection(
                settings = uiState.privacySettings,
                onSettingsChange = { settings ->
                    viewModel.setPrivacySettings(settings)
                    onScreenPrivacyChanged(settings.screenPrivacyEnabled)
                }
            )

            // Service credential sections
            AiService.entries.forEach { service ->
                val state = uiState.serviceStates[service] ?: ServiceCredentialState()
                ServiceCredentialSection(
                    service = service,
                    state = state,
                    onFieldChange = { field, value -> viewModel.updateField(service, field, value) },
                    onStartAccountLink = { viewModel.startAccountLink(service) },
                    onOpenAccountLink = { url -> openAuthUrl(context, url) },
                    onValidate = { viewModel.validateCredential(service) },
                    onDisconnect = { viewModel.showDisconnectConfirmDialog(service) }
                )
            }

            // Refresh interval
            RefreshIntervalSection(
                currentMinutes = uiState.refreshIntervalMinutes,
                onIntervalChange = { viewModel.setRefreshInterval(it) }
            )

            // Danger zone
            DangerZoneSection(
                onDeleteAll = { viewModel.showDeleteConfirmDialog() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteAllCredentials()
                viewModel.dismissDeleteConfirmDialog()
            },
            onDismiss = { viewModel.dismissDeleteConfirmDialog() }
        )
    }

    uiState.disconnectConfirmService?.let { service ->
        DisconnectConfirmDialog(
            service = service,
            onConfirm = { viewModel.disconnectService(service) },
            onDismiss = { viewModel.dismissDisconnectConfirmDialog() }
        )
    }
}

@Composable
private fun ServiceCredentialSection(
    service: AiService,
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onStartAccountLink: () -> Unit,
    onOpenAccountLink: (String) -> Unit,
    onValidate: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = service.displayName,
                    tint = Color(service.brandColor),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (state.isConnected) "Connected" else "Not connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.accessToken,
                onValueChange = { onFieldChange("accessToken", it) },
                label = { Text(if (service == AiService.COPILOT) "GitHub OAuth Token" else "Access Token") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = secretKeyboardOptions(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (service != AiService.COPILOT) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.refreshToken,
                    onValueChange = { onFieldChange("refreshToken", it) },
                    label = { Text("Refresh Token") },
                    supportingText = if (service == AiService.CLAUDE) {
                        { Text("Required for auto-refresh (tokens expire every 8h)") }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = secretKeyboardOptions(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Service-specific fields
            when (service) {
                AiService.CODEX -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.accountId,
                        onValueChange = { onFieldChange("accountId", it) },
                        label = { Text("Account ID (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                AiService.GEMINI -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.oauthClientId,
                        onValueChange = { onFieldChange("oauthClientId", it) },
                        label = { Text("OAuth Client ID") },
                        supportingText = { Text("From Google Cloud Console") },
                        keyboardOptions = secretKeyboardOptions(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.oauthClientSecret,
                        onValueChange = { onFieldChange("oauthClientSecret", it) },
                        label = { Text("OAuth Client Secret (optional)") },
                        supportingText = { Text("Only needed for confidential/web OAuth clients.") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = secretKeyboardOptions(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (state.expiresAtDisplay.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.expiresAtDisplay,
                            onValueChange = {},
                            label = { Text("Token Expiry") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = false,
                            singleLine = true
                        )
                    }
                }
                else -> {} // Claude has no extra fields
            }

            if (service.supportsAccountLink()) {
                Spacer(modifier = Modifier.height(12.dp))

                AccountLinkControls(
                    service = service,
                    state = state,
                    onStartAccountLink = onStartAccountLink,
                    onOpenAccountLink = onOpenAccountLink
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Validate button + result
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onValidate,
                    enabled = !state.isValidating && state.accessToken.isNotBlank()
                ) {
                    if (state.isValidating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Validate and Connect")
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (state.isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = !state.isValidating,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect")
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }

                when (state.validationResult) {
                    is ValidationResult.Success -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Valid",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Valid", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    }
                    is ValidationResult.Failure -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Invalid",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            state.validationResult.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    null -> {}
                }
            }

            if (state.hasUnsavedChanges) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Changes are not active until validation succeeds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AccountLinkControls(
    service: AiService,
    state: ServiceCredentialState,
    onStartAccountLink: () -> Unit,
    onOpenAccountLink: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when (service) {
                AiService.CODEX -> "Sign in with ChatGPT using OpenAI's device-code flow. Tokens are saved only after validation."
                AiService.GEMINI -> "Sign in with Google using device-code OAuth. Enter your OAuth Client ID above first."
                AiService.COPILOT -> "Sign in with GitHub's device flow. The app stores the OAuth token only after validation."
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onStartAccountLink,
                enabled = !state.isAccountLinking &&
                    !state.isValidating &&
                    (service != AiService.GEMINI || state.oauthClientId.isNotBlank())
            ) {
                if (state.isAccountLinking && state.accountLinkPrompt == null) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.isAccountLinking) "Waiting for sign-in" else "Connect account")
            }
        }

        state.accountLinkPrompt?.let { prompt ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Enter this code in the sign-in page:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = prompt.userCode,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Expires: ${prompt.expiresAtDisplay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { onOpenAccountLink(prompt.verificationUrl) }) {
                        Text("Open sign-in page")
                    }
                }
            }
        }
    }
}

private fun AiService.supportsAccountLink(): Boolean {
    return this == AiService.CODEX || this == AiService.GEMINI || this == AiService.COPILOT
}

private fun openAuthUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

private fun secretKeyboardOptions(): KeyboardOptions {
    return KeyboardOptions(
        keyboardType = KeyboardType.Password,
        autoCorrectEnabled = false
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIntervalSection(
    currentMinutes: Long,
    onIntervalChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Refresh Interval",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            val options = listOf(15L to "15 min", 30L to "30 min", 60L to "1 hour", 0L to "Manual")
            val selectedIndex = options.indexOfFirst { it.first == currentMinutes }.takeIf { it >= 0 } ?: 1

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (minutes, label) ->
                    SegmentedButton(
                        selected = index == selectedIndex,
                        onClick = { onIntervalChange(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Quota status and reset alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun DangerZoneSection(onDeleteAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDeleteAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete All Credentials")
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete All Credentials") },
        text = {
            Column {
                Text("This will permanently delete all saved API credentials. Type DELETE to confirm.")
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type DELETE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmText == "DELETE"
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PrivacySection(
    settings: PrivacySettings,
    onSettingsChange: (PrivacySettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Privacy",
                style = MaterialTheme.typography.titleMedium
            )

            PrivacyToggle(
                title = "Block screenshots and Recents preview",
                subtitle = "Protects token entry and account screens.",
                checked = settings.screenPrivacyEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(screenPrivacyEnabled = it))
                }
            )
            PrivacyToggle(
                title = "Redact lock-screen notifications",
                subtitle = "Shows a neutral lock-screen notification.",
                checked = settings.lockScreenRedactionEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(lockScreenRedactionEnabled = it))
                }
            )
            PrivacyToggle(
                title = "Redact notification quota details",
                subtitle = "Hides usage numbers from the notification shade.",
                checked = settings.notificationRedactionEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(notificationRedactionEnabled = it))
                }
            )
            PrivacyToggle(
                title = "Redact widget quota details",
                subtitle = "Keeps the widget visible without usage numbers.",
                checked = settings.widgetRedactionEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(widgetRedactionEnabled = it))
                }
            )
        }
    }
}

@Composable
private fun PrivacyToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DisconnectConfirmDialog(
    service: AiService,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect ${service.displayName}") },
        text = { Text("This deletes saved credentials and cached reset data for ${service.displayName}.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
