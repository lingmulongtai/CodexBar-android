package com.codexbar.android.feature.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.update.*
import com.codexbar.android.ui.components.CodexBarButton
import com.codexbar.android.ui.components.CodexBarCard
import com.codexbar.android.ui.components.CodexBarTextButton
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun UpdateDownloadPanel(available: AvailableUpdate? = null, viewModel: UpdateDownloadViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var installError by remember(state.id) { mutableStateOf<UpdateFailure?>(null) }
    var permissionRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val launchInstaller: (UUID) -> Unit = { id ->
        if (!installing) scope.launch {
            installing = true
            installError = null
            try { context.startActivity(viewModel.installerIntent(id)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { installError = (failure as? UpdateDownloadException)?.reason ?: UpdateFailure.UNAVAILABLE }
            finally { installing = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val id = permissionRequest?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        permissionRequest = null
        if (id != null && context.packageManager.canRequestPackageInstalls()) launchInstaller(id)
    }
    if (state.phase == UpdateDownloadPhase.IDLE && available == null && !pending && error == null) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.version.isNotEmpty() && available?.version != state.version)
            Text(stringResource(R.string.app_info_update_available, state.version))
        val status = when (state.phase) {
            UpdateDownloadPhase.QUEUED -> R.string.update_waiting
            UpdateDownloadPhase.DOWNLOADING -> R.string.update_downloading
            UpdateDownloadPhase.VERIFYING -> R.string.update_verifying
            UpdateDownloadPhase.READY -> R.string.update_ready
            UpdateDownloadPhase.CANCELLED -> R.string.update_cancelled
            else -> null
        }
        status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        if (state.phase == UpdateDownloadPhase.DOWNLOADING && state.total > 0) {
            LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.update_progress, state.percent,
                Formatter.formatShortFileSize(context, state.received), Formatter.formatShortFileSize(context, state.total)))
        } else if (state.busy || pending || installing) LinearProgressIndicator(Modifier.fillMaxWidth())
        val failure = installError ?: error ?: state.failure ?: UpdateFailure.UNAVAILABLE.takeIf {
            state.phase == UpdateDownloadPhase.FAILED || available != null && available.artifact == null
        }
        failure?.let { Text(stringResource(it.message()), color = MaterialTheme.colorScheme.error) }
        when {
            state.busy -> CodexBarTextButton(onClick = viewModel::cancel, enabled = !pending) { Text(stringResource(R.string.action_cancel)) }
            state.phase == UpdateDownloadPhase.READY -> {
                Text(stringResource(R.string.update_permission_help), style = MaterialTheme.typography.bodySmall)
                CodexBarButton(enabled = !installing && !pending, onClick = {
                    val id = state.id ?: return@CodexBarButton
                    if (context.packageManager.canRequestPackageInstalls()) launchInstaller(id)
                    else runCatching {
                        permissionRequest = id.toString()
                        permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    }.onFailure { installError = UpdateFailure.UNAVAILABLE }
                }) { Text(if (installing) stringResource(R.string.update_install_preparing)
                    else stringResource(R.string.update_install, state.version)) }
                if (available?.artifact != null && isNewerStableVersion(available.version, state.version)) {
                    Text(stringResource(R.string.app_info_update_available, available.version))
                    CodexBarButton(enabled = !pending && !installing, onClick = { viewModel.start(available) }) {
                        Text(stringResource(R.string.action_download_update))
                    }
                } else if (failure != null) {
                    CodexBarTextButton(enabled = !pending && !installing, onClick = viewModel::retry) {
                        Text(stringResource(R.string.update_retry))
                    }
                }
            }
            else -> CodexBarButton(enabled = !pending, onClick = {
                if (state.phase == UpdateDownloadPhase.IDLE && available?.artifact != null) viewModel.start(available)
                else viewModel.retry()
            }) { Text(stringResource(if (state.phase == UpdateDownloadPhase.IDLE && available?.artifact != null) R.string.action_download_update else R.string.update_retry)) }
        }
    }
}

@Composable
internal fun UpdateDownloadDialog(update: AvailableUpdate, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CodexBarCard(Modifier.fillMaxWidth()) {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.update_available_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.update_available_message, update.version))
                UpdateDownloadPanel(update)
                CodexBarTextButton(onClick = onDismiss) { Text(stringResource(R.string.update_background_continue)) }
            }
        }
    }
}

private fun UpdateFailure.message(): Int = when (this) {
    UpdateFailure.NETWORK -> R.string.update_error_network
    UpdateFailure.INTEGRITY -> R.string.update_error_integrity
    UpdateFailure.PACKAGE -> R.string.update_error_package
    UpdateFailure.VERSION -> R.string.update_error_version
    UpdateFailure.SIGNER -> R.string.update_error_signer
    UpdateFailure.STORAGE -> R.string.update_error_storage
    UpdateFailure.UNAVAILABLE -> R.string.update_error_unavailable
}
