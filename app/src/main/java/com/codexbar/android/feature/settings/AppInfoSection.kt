package com.codexbar.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.BuildConfig
import com.codexbar.android.R
import com.codexbar.android.feature.update.UpdateDownloadPanel
import com.codexbar.android.core.update.UpdateCheckResult
import com.codexbar.android.ui.components.CodexBarButton
import com.codexbar.android.ui.components.CodexBarCard
import com.codexbar.android.ui.components.CodexBarOutlinedButton

private const val ANDROID_PROJECT = "https://github.com/lingmulongtai/CodexBar-android"
private const val WINDOWS_PROJECT = "https://github.com/lingmulongtai/Win-CodexBar-Mobile-Connect"
private const val RELEASES = "$ANDROID_PROJECT/releases/latest"

@Composable
internal fun AppInfoSection(viewModel: AppInfoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var browserError by remember { mutableStateOf(false) }
    fun open(url: String) { browserError = runCatching { uri.openUri(url) }.isFailure }
    CodexBarCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.codexbar_icon_art),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                )
                Text(stringResource(R.string.app_info_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.app_info_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium)
            CodexBarOutlinedButton(onClick = { open(ANDROID_PROJECT) }) { Text(stringResource(R.string.app_info_android_project)) }
            CodexBarOutlinedButton(onClick = { open(WINDOWS_PROJECT) }) { Text(stringResource(R.string.app_info_windows_project)) }
            CodexBarOutlinedButton(onClick = { open(RELEASES) }) { Text(stringResource(R.string.app_info_releases)) }
            CodexBarButton(onClick = viewModel::checkForUpdates, enabled = !state.checking) {
                Text(stringResource(R.string.app_info_check_updates))
            }
            if (state.checking) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(stringResource(R.string.app_info_checking), style = MaterialTheme.typography.bodySmall)
            }
            when (val result = state.result) {
                is UpdateCheckResult.Available -> {
                    Text(stringResource(R.string.app_info_update_available, result.update.version))
                }
                UpdateCheckResult.UpToDate -> Text(stringResource(R.string.app_info_up_to_date))
                UpdateCheckResult.VersionNotComparable -> Text(stringResource(R.string.app_info_preview_version))
                UpdateCheckResult.Unavailable -> Text(stringResource(R.string.app_info_update_unavailable), color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
            UpdateDownloadPanel((state.result as? UpdateCheckResult.Available)?.update)
            if (browserError) Text(stringResource(R.string.app_info_browser_unavailable), color = MaterialTheme.colorScheme.error)
        }
    }
}
