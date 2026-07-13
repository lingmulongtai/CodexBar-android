package com.codexbar.android.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.security.PrivacySettings
import com.codexbar.android.core.workmanager.RefreshIntervalPolicy
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onScreenPrivacyChanged: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(context.canPostNotifications()) }
    var promotedUpdatesAllowed by remember { mutableStateOf(context.canPostPromotedNotifications()) }
    var enableNotificationsAfterSettings by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(AppLanguage.current()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val allowed = granted && context.canPostNotifications()
        notificationsAllowed = allowed
        viewModel.setNotificationsEnabled(allowed)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val allowed = context.canPostNotifications()
        notificationsAllowed = allowed
        promotedUpdatesAllowed = context.canPostPromotedNotifications()
        if (enableNotificationsAfterSettings) {
            enableNotificationsAfterSettings = false
            if (allowed) {
                viewModel.setNotificationsEnabled(true)
            } else if (uiState.notificationsEnabled) {
                viewModel.setNotificationsEnabled(false)
            }
        } else if (!allowed && uiState.notificationsEnabled) {
            viewModel.setNotificationsEnabled(false)
        }
        viewModel.syncMonitoringState()
    }

    LaunchedEffect(uiState.isMonitoring) {
        if (!uiState.isMonitoring) return@LaunchedEffect
        while (true) {
            delay(60_000L)
            viewModel.syncMonitoringState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
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
                notificationsAllowed = notificationsAllowed,
                isMonitoring = uiState.isMonitoring,
                durationMinutes = uiState.monitoringDurationMinutes,
                remainingMinutes = uiState.monitoringRemainingMinutes,
                hasConnectedService = uiState.serviceStates.values.any { it.isConnected },
                promotedUpdatesAllowed = promotedUpdatesAllowed,
                onToggle = { requested ->
                    when {
                        !requested -> viewModel.setNotificationsEnabled(false)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !context.hasNotificationPermission() -> {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        !context.canPostNotifications() -> {
                            enableNotificationsAfterSettings = true
                            openAppNotificationSettings(context)
                        }
                        else -> viewModel.setNotificationsEnabled(true)
                    }
                },
                onDurationChange = viewModel::setMonitoringDuration,
                onStartMonitoring = viewModel::startMonitoring,
                onStopMonitoring = viewModel::stopMonitoring,
                onOpenNotificationSettings = {
                    enableNotificationsAfterSettings = true
                    openAppNotificationSettings(context)
                },
                onOpenPromotionSettings = { openPromotionSettings(context) }
            )

            LanguageSection(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { language ->
                    selectedLanguage = language
                    language.apply()
                    viewModel.refreshLocalizedSurfaces()
                }
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
                    onCopyAccountCode = { code -> copyToClipboard(context, code) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSection(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
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
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.language_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEachIndexed { index, language ->
                    SegmentedButton(
                        selected = selectedLanguage == language,
                        onClick = { onLanguageSelected(language) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppLanguage.entries.size
                        )
                    ) {
                        Text(stringResource(language.labelRes))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCredentialSection(
    service: AiService,
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onStartAccountLink: () -> Unit,
    onOpenAccountLink: (String) -> Unit,
    onCopyAccountCode: (String) -> Unit,
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
                    text = stringResource(
                        if (state.isConnected) R.string.status_connected else R.string.status_not_connected
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (service == AiService.CLAUDE) {
                Text(
                    text = stringResource(R.string.credential_claude_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.accessToken,
                onValueChange = { onFieldChange("accessToken", it) },
                label = {
                    Text(
                        stringResource(
                            if (service == AiService.COPILOT) {
                                R.string.credential_github_oauth_token
                            } else {
                                R.string.credential_access_token
                            }
                        )
                    )
                },
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
                    label = { Text(stringResource(R.string.credential_refresh_token)) },
                    supportingText = if (service == AiService.CLAUDE) {
                        { Text(stringResource(R.string.credential_claude_refresh_support)) }
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
                        label = { Text(stringResource(R.string.credential_account_id_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                AiService.GEMINI -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.oauthClientId,
                        onValueChange = { onFieldChange("oauthClientId", it) },
                        label = { Text(stringResource(R.string.credential_oauth_client_id)) },
                        supportingText = {
                            Text(stringResource(R.string.credential_google_client_support))
                        },
                        keyboardOptions = secretKeyboardOptions(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (state.expiresAtDisplay.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.expiresAtDisplay,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.credential_token_expiry)) },
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
                    onOpenAccountLink = onOpenAccountLink,
                    onCopyAccountCode = onCopyAccountCode
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onValidate,
                    enabled = !state.isValidating && state.accessToken.isNotBlank(),
                    modifier = if (state.isConnected) Modifier.weight(1f) else Modifier.fillMaxWidth()
                ) {
                    if (state.isValidating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            when {
                                state.isConnected && state.hasUnsavedChanges -> R.string.action_save_changes
                                state.isConnected -> R.string.action_revalidate
                                else -> R.string.action_validate_connect
                            }
                        )
                    )
                }

                if (state.isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = !state.isValidating,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_disconnect))
                    }
                }
            }

            // Validation result
            when (state.validationResult) {
                is ValidationResult.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.content_description_valid),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.status_valid),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is ValidationResult.Failure -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.content_description_invalid),
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
                }
                null -> {}
            }

            if (state.hasUnsavedChanges) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.credential_changes_pending),
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
    onOpenAccountLink: (String) -> Unit,
    onCopyAccountCode: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when (service) {
                AiService.CODEX -> stringResource(R.string.account_link_codex_description)
                AiService.GEMINI -> stringResource(R.string.account_link_gemini_description)
                AiService.COPILOT -> stringResource(R.string.account_link_copilot_description)
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
                Text(
                    stringResource(
                        if (state.isAccountLinking) {
                            R.string.action_waiting_for_sign_in
                        } else {
                            R.string.action_connect_account
                        }
                    )
                )
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
                        text = stringResource(R.string.account_link_enter_code),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = prompt.userCode,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onCopyAccountCode(prompt.userCode) }) {
                            Text(stringResource(R.string.action_copy))
                        }
                    }
                    Text(
                        text = stringResource(R.string.account_link_expires, prompt.expiresAtDisplay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { onOpenAccountLink(prompt.verificationUrl) }) {
                        Text(stringResource(R.string.action_open_sign_in_page))
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

private fun Context.hasNotificationPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.canPostNotifications(): Boolean {
    return hasNotificationPermission() &&
        NotificationManagerCompat.from(this).areNotificationsEnabled()
}

private fun Context.canPostPromotedNotifications(): Boolean {
    if (Build.VERSION.SDK_INT < 36) return false
    val manager = getSystemService(NotificationManager::class.java) ?: return false
    return manager.canPostPromotedNotifications()
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}

private fun openPromotionSettings(context: Context) {
    if (Build.VERSION.SDK_INT < 36) return
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    } catch (_: ActivityNotFoundException) {
        openAppNotificationSettings(context)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(context.getString(R.string.clipboard_sign_in_code), text).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, context.getString(R.string.message_code_copied), Toast.LENGTH_SHORT).show()
    }
}

private fun secretKeyboardOptions(): KeyboardOptions {
    return KeyboardOptions(
        keyboardType = KeyboardType.Password,
        autoCorrectEnabled = false
    )
}

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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val automaticRefresh = currentMinutes > RefreshIntervalPolicy.MANUAL_MINUTES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.background_refresh_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (automaticRefresh) {
                            stringResource(
                                R.string.background_refresh_every,
                                formatMonitoringDuration(currentMinutes)
                            )
                        } else {
                            stringResource(R.string.background_refresh_manual_only)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = automaticRefresh,
                    onCheckedChange = { enabled ->
                        onIntervalChange(
                            if (enabled) {
                                RefreshIntervalPolicy.DEFAULT_MINUTES
                            } else {
                                RefreshIntervalPolicy.MANUAL_MINUTES
                            }
                        )
                    }
                )
            }

            if (automaticRefresh) {
                val refreshRange = RefreshIntervalPolicy.MIN_MINUTES.toFloat()..
                    RefreshIntervalPolicy.MAX_MINUTES.toFloat()
                val refreshSteps = (
                    (RefreshIntervalPolicy.MAX_MINUTES - RefreshIntervalPolicy.MIN_MINUTES) /
                        RefreshIntervalPolicy.STEP_MINUTES - 1
                    ).toInt()
                Text(
                    text = formatMonitoringDuration(currentMinutes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = currentMinutes.toFloat(),
                    onValueChange = { value ->
                        onIntervalChange(
                            RefreshIntervalPolicy.normalize(value.roundToInt().toLong())
                        )
                    },
                    valueRange = refreshRange,
                    steps = refreshSteps
                )
            }

            Text(
                text = stringResource(R.string.background_refresh_guidance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationsSection(
    enabled: Boolean,
    notificationsAllowed: Boolean,
    isMonitoring: Boolean,
    durationMinutes: Long,
    remainingMinutes: Long?,
    hasConnectedService: Boolean,
    promotedUpdatesAllowed: Boolean,
    onToggle: (Boolean) -> Unit,
    onDurationChange: (Long) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenPromotionSettings: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notifications_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.notifications_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled && notificationsAllowed,
                    onCheckedChange = onToggle
                )
            }

            if (!notificationsAllowed) {
                Text(
                    text = stringResource(R.string.notifications_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onOpenNotificationSettings) {
                    Text(stringResource(R.string.action_open_notification_settings))
                }
            }

            Text(
                text = when {
                    isMonitoring -> {
                        val remaining = if (remainingMinutes == null) {
                            stringResource(R.string.notifications_live_ending_soon)
                        } else {
                            formatMonitoringDuration(remainingMinutes)
                        }
                        stringResource(R.string.notifications_live_running, remaining)
                    }
                    enabled && notificationsAllowed -> stringResource(R.string.notifications_ready)
                    else -> stringResource(R.string.notifications_off)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isMonitoring) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Text(
                text = stringResource(R.string.notifications_session_length),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = formatMonitoringDuration(durationMinutes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = durationMinutes.toFloat(),
                onValueChange = { value ->
                    val minutes = ((value / 15f).roundToInt() * 15)
                        .coerceIn(15, 180)
                        .toLong()
                    onDurationChange(minutes)
                },
                valueRange = 15f..180f,
                steps = 10,
                enabled = !isMonitoring
            )
            Text(
                text = stringResource(R.string.notifications_schedule_guidance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isMonitoring) {
                OutlinedButton(
                    onClick = onStopMonitoring,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_stop_live_monitor))
                }
            } else {
                Button(
                    onClick = onStartMonitoring,
                    enabled = enabled && notificationsAllowed && hasConnectedService,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_start_live_monitor))
                }
            }

            if (!hasConnectedService) {
                Text(
                    text = stringResource(R.string.notifications_service_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (Build.VERSION.SDK_INT >= 36) {
                if (promotedUpdatesAllowed) {
                    Text(
                        text = stringResource(R.string.notifications_promoted_allowed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    OutlinedButton(
                        onClick = onOpenPromotionSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_allow_promoted_updates))
                    }
                }
            }
        }
    }
}

@Composable
private fun formatMonitoringDuration(minutes: Long): String {
    if (minutes < 60) return stringResource(R.string.duration_minutes, minutes)
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0L) {
        stringResource(R.string.duration_hours, hours)
    } else {
        stringResource(R.string.duration_hours_minutes, hours, remainder)
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
                text = stringResource(R.string.danger_zone_title),
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
                Text(stringResource(R.string.delete_all_credentials))
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
        title = { Text(stringResource(R.string.delete_all_credentials)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_all_credentials_confirmation))
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text(stringResource(R.string.delete_all_credentials_type)) },
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
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
                text = stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.titleMedium
            )

            PrivacyToggle(
                title = stringResource(R.string.privacy_screen_title),
                subtitle = stringResource(R.string.privacy_screen_description),
                checked = settings.screenPrivacyEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(screenPrivacyEnabled = it))
                }
            )
            PrivacyToggle(
                title = stringResource(R.string.privacy_lock_screen_title),
                subtitle = stringResource(R.string.privacy_lock_screen_description),
                checked = settings.lockScreenRedactionEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(lockScreenRedactionEnabled = it))
                }
            )
            PrivacyToggle(
                title = stringResource(R.string.privacy_notification_title),
                subtitle = stringResource(R.string.privacy_notification_description),
                checked = settings.notificationRedactionEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(notificationRedactionEnabled = it))
                }
            )
            PrivacyToggle(
                title = stringResource(R.string.privacy_widget_title),
                subtitle = stringResource(R.string.privacy_widget_description),
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
        title = { Text(stringResource(R.string.disconnect_service_title, service.displayName)) },
        text = {
            Text(stringResource(R.string.disconnect_service_message, service.displayName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_disconnect), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
