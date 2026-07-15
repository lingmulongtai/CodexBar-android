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
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontFamily
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
import com.codexbar.android.ui.components.providerIcon
import com.codexbar.android.ui.theme.providerVisualStyle
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    showBackButton: Boolean = true,
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
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_accounts_title),
                    description = stringResource(R.string.settings_accounts_description)
                )

                AiService.entries.forEach { service ->
                    val state = uiState.serviceStates[service] ?: ServiceCredentialState()
                    ServiceCredentialSection(
                        service = service,
                        state = state,
                        onFieldChange = { field, value ->
                            viewModel.updateField(service, field, value)
                        },
                        onStartAccountLink = { viewModel.startAccountLink(service) },
                        onOpenAccountLink = { url -> openAuthUrl(context, url) },
                        onCopyAccountCode = { code ->
                            copyToClipboard(
                                context = context,
                                text = code,
                                labelRes = R.string.clipboard_sign_in_code,
                                sensitive = true
                            )
                        },
                        onCopySetupCommand = { command ->
                            copyToClipboard(
                                context = context,
                                text = command,
                                labelRes = R.string.clipboard_setup_command,
                                sensitive = false
                            )
                        },
                        onOpenSetupGuide = {
                            openAuthUrl(context, accountGuideUrl(service))
                        },
                        onValidate = { viewModel.validateCredential(service) },
                        onDisconnect = { viewModel.showDisconnectConfirmDialog(service) }
                    )
                }

                SettingsSectionHeader(
                    title = stringResource(R.string.settings_monitoring_title),
                    description = stringResource(R.string.settings_monitoring_description)
                )

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
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
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

                RefreshIntervalSection(
                    currentMinutes = uiState.refreshIntervalMinutes,
                    onIntervalChange = { viewModel.setRefreshInterval(it) }
                )

                SettingsSectionHeader(
                    title = stringResource(R.string.settings_preferences_title),
                    description = stringResource(R.string.settings_preferences_description)
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

                DangerZoneSection(
                    onDeleteAll = { viewModel.showDeleteConfirmDialog() }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
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
private fun SettingsSectionHeader(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
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
    onCopySetupCommand: (String) -> Unit,
    onOpenSetupGuide: () -> Unit,
    onValidate: () -> Unit,
    onDisconnect: () -> Unit
) {
    val visualStyle = providerVisualStyle(service)
    var showManualSetup by rememberSaveable(service) {
        mutableStateOf(service == AiService.CLAUDE)
    }
    LaunchedEffect(state.hasUnsavedChanges) {
        if (state.hasUnsavedChanges) showManualSetup = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = visualStyle.shape,
        colors = CardDefaults.cardColors(containerColor = visualStyle.container),
        border = BorderStroke(1.dp, visualStyle.accent.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = MaterialTheme.shapes.small,
                    color = visualStyle.accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, visualStyle.accent.copy(alpha = 0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = service.providerIcon(),
                            contentDescription = service.displayName,
                            tint = visualStyle.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.displayName,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                val statusColor = if (state.isConnected) {
                    visualStyle.accent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(
                            if (state.isConnected) {
                                R.string.status_connected
                            } else {
                                R.string.status_not_connected
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            if (service == AiService.GEMINI) {
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
            }

            if (service.supportsAccountLink()) {
                AccountLinkControls(
                    service = service,
                    state = state,
                    accent = visualStyle.accent,
                    onAccent = visualStyle.onAccent,
                    onStartAccountLink = onStartAccountLink,
                    onOpenAccountLink = onOpenAccountLink,
                    onCopyAccountCode = onCopyAccountCode
                )
            } else {
                ClaudeSetupGuide(
                    accent = visualStyle.accent,
                    onCopySetupCommand = onCopySetupCommand
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.76f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = visualStyle.accent,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = stringResource(R.string.account_security_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            TextButton(onClick = onOpenSetupGuide) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_view_setup_guide))
            }

            HorizontalDivider(color = visualStyle.accent.copy(alpha = 0.2f))

            TextButton(
                onClick = { showManualSetup = !showManualSetup },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (showManualSetup) {
                        Icons.Rounded.ExpandLess
                    } else {
                        Icons.Rounded.ExpandMore
                    },
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.account_manual_setup),
                    modifier = Modifier.weight(1f)
                )
            }

            if (showManualSetup) {
                Text(
                    text = stringResource(R.string.account_manual_setup_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ManualCredentialFields(
                    service = service,
                    state = state,
                    onFieldChange = onFieldChange,
                    onValidate = onValidate
                )
            }

            if (state.isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = !state.isValidating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_disconnect))
                }
            }

            CredentialValidationResult(state.validationResult, visualStyle.accent)

            if (state.hasUnsavedChanges) {
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
private fun ClaudeSetupGuide(
    accent: Color,
    onCopySetupCommand: (String) -> Unit
) {
    var copied by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.credential_claude_step_command),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = CLAUDE_SETUP_COMMAND,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = accent
                    )
                }
                OutlinedButton(
                    onClick = {
                        onCopySetupCommand(CLAUDE_SETUP_COMMAND)
                        copied = true
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (copied) R.string.action_copied else R.string.action_copy_command
                        )
                    )
                }
            }
            Text(
                text = stringResource(R.string.credential_claude_step_paste),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManualCredentialFields(
    service: AiService,
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onValidate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

        if (service == AiService.CODEX) {
            OutlinedTextField(
                value = state.accountId,
                onValueChange = { onFieldChange("accountId", it) },
                label = { Text(stringResource(R.string.credential_account_id_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (service == AiService.GEMINI && state.expiresAtDisplay.isNotBlank()) {
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

        OutlinedButton(
            onClick = onValidate,
            enabled = !state.isValidating && state.accessToken.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
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
    }
}

@Composable
private fun CredentialValidationResult(
    validationResult: ValidationResult?,
    successColor: Color
) {
    when (validationResult) {
        is ValidationResult.Success -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.content_description_valid),
                    tint = successColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.status_valid),
                    color = successColor,
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
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    validationResult.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun AccountLinkControls(
    service: AiService,
    state: ServiceCredentialState,
    accent: Color,
    onAccent: Color,
    onStartAccountLink: () -> Unit,
    onOpenAccountLink: (String) -> Unit,
    onCopyAccountCode: (String) -> Unit
) {
    val prompt = state.accountLinkPrompt
    var copiedCode by rememberSaveable(service) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = accent.copy(alpha = 0.12f)
        ) {
            Text(
                text = stringResource(R.string.account_connect_recommended),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        Text(
            text = when (service) {
                AiService.CODEX -> stringResource(R.string.account_link_codex_description)
                AiService.GEMINI -> stringResource(R.string.account_link_gemini_description)
                AiService.COPILOT -> stringResource(R.string.account_link_copilot_description)
                AiService.CLAUDE -> stringResource(R.string.credential_claude_instructions)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onStartAccountLink,
            enabled = !state.isAccountLinking &&
                !state.isValidating &&
                (service != AiService.GEMINI || state.oauthClientId.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = onAccent
            )
        ) {
            if (state.isAccountLinking && prompt == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = onAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                stringResource(
                    when {
                        state.isAccountLinking -> R.string.action_waiting_for_sign_in
                        state.isConnected -> R.string.action_reconnect_account
                        else -> R.string.action_connect_account
                    }
                )
            )
        }

        prompt?.let {
            val clipboardCode = remember(prompt.userCode) {
                deviceCodeForClipboard(prompt.userCode)
            }
            val verificationHost = remember(prompt.verificationUrl) {
                Uri.parse(prompt.verificationUrl).host ?: prompt.verificationUrl
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account_link_steps_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = accent
                    )
                    AccountLinkInstructionStep(
                        number = 1,
                        text = stringResource(R.string.account_link_step_copy),
                        accent = accent
                    )
                    Text(
                        text = stringResource(R.string.account_link_enter_code),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prompt.userCode,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = accent
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onCopyAccountCode(clipboardCode)
                            copiedCode = clipboardCode
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = onAccent
                        )
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (copiedCode == clipboardCode) {
                                    R.string.action_copied
                                } else {
                                    R.string.action_copy_sign_in_code
                                }
                            )
                        )
                    }
                    AccountLinkInstructionStep(
                        number = 2,
                        text = stringResource(
                            R.string.account_link_step_open,
                            verificationHost
                        ),
                        accent = accent
                    )
                    OutlinedButton(
                        onClick = { onOpenAccountLink(prompt.verificationUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_open_sign_in_page))
                    }
                    AccountLinkInstructionStep(
                        number = 3,
                        text = stringResource(R.string.account_link_step_authorize),
                        accent = accent
                    )
                    AccountLinkInstructionStep(
                        number = 4,
                        text = stringResource(R.string.account_link_step_return),
                        accent = accent
                    )
                    Text(
                        text = stringResource(R.string.account_link_expires, prompt.expiresAtDisplay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.account_link_retry_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = accent.copy(alpha = 0.2f))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.account_link_code_safety),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountLinkInstructionStep(
    number: Int,
    text: String,
    accent: Color
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = accent.copy(alpha = 0.16f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

internal fun deviceCodeForClipboard(userCode: String): String {
    return userCode
        .filter(Char::isLetterOrDigit)
        .uppercase(Locale.ROOT)
}

private fun AiService.supportsAccountLink(): Boolean {
    return this == AiService.CODEX || this == AiService.GEMINI || this == AiService.COPILOT
}

internal fun accountGuideUrl(service: AiService): String {
    val anchor = when (service) {
        AiService.CLAUDE -> "claude-anthropic"
        AiService.CODEX -> "codex-openai--chatgpt"
        AiService.GEMINI -> "gemini-google"
        AiService.COPILOT -> "github-copilot"
    }
    return "$ACCOUNT_GUIDE_BASE_URL#$anchor"
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

private fun copyToClipboard(
    context: Context,
    text: String,
    @StringRes labelRes: Int,
    sensitive: Boolean
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(context.getString(labelRes), text).apply {
        if (sensitive) {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
    }
    clipboard.setPrimaryClip(clip)

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, context.getString(R.string.message_code_copied), Toast.LENGTH_SHORT).show()
    }
}

private const val CLAUDE_SETUP_COMMAND = "claude setup-token"
private const val ACCOUNT_GUIDE_BASE_URL =
    "https://github.com/lingmulongtai/CodexBar-android"

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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
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
