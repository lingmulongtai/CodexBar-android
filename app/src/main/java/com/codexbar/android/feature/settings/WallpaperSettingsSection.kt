package com.codexbar.android.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.data.WallpaperStatus
import com.codexbar.android.ui.components.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.launch

@Composable
fun WallpaperSettingsSection(model: DailyWallpaperViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var showApi by remember { mutableStateOf(false) }
    // Never put an API key into saved instance state or read the stored key back into the UI.
    var key by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun save(enabled: Boolean, replacement: String?) {
        scope.launch {
            saving = true
            try { model.save(enabled, replacement); key = "" }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { saving = false }
        }
    }
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.wallpaper_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.wallpaper_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CodexBarSwitch(state.preferences.enabled, { enabled ->
                    save(enabled, null)
                }, enabled = !saving)
            }
            state.bitmap?.let { bitmap ->
                Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(110.dp))
            }
            Text(stringResource(when (state.status) {
                WallpaperStatus.Updating -> R.string.wallpaper_updating
                WallpaperStatus.Offline -> R.string.wallpaper_offline
                WallpaperStatus.ApiUnavailable -> R.string.wallpaper_api_unavailable
                WallpaperStatus.Ready -> if (state.preferences.hasAccessKey) R.string.wallpaper_editorial else R.string.wallpaper_curated
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.day?.let { Text(stringResource(R.string.wallpaper_selected_day, it), style = MaterialTheme.typography.labelSmall) }
            state.photo?.let { photo ->
                CodexBarTextButton(onClick = { uri.openUri(photo.profileUrl.toHttpUrl().newBuilder().setQueryParameter("utm_source", "codexbar_android").setQueryParameter("utm_medium", "referral").build().toString()) }) {
                    Text(stringResource(R.string.wallpaper_credit, photo.photographer))
                }
            }
            CodexBarOutlinedButton(onClick = { model.refresh(true) }, enabled = state.preferences.enabled && state.status != WallpaperStatus.Updating) {
                Text(stringResource(R.string.wallpaper_refresh))
            }
            CodexBarTextButton(onClick = { showApi = !showApi }) { Text(stringResource(R.string.wallpaper_api_settings)) }
            if (failed) Text(stringResource(R.string.wallpaper_key_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (showApi) {
                Text(stringResource(R.string.wallpaper_key_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(key, { key = it.trim(); failed = false }, label = { Text(stringResource(R.string.wallpaper_access_key)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    isError = failed, modifier = Modifier.fillMaxWidth())
                CodexBarButton(onClick = {
                    if (!key.matches(Regex("[A-Za-z0-9_-]{16,128}"))) failed = true
                    else save(state.preferences.enabled, key)
                }, enabled = !saving && key.isNotEmpty()) { Text(stringResource(R.string.wallpaper_save_key)) }
                if (state.preferences.hasAccessKey) CodexBarTextButton(onClick = {
                    save(state.preferences.enabled, "")
                }, enabled = !saving) { Text(stringResource(R.string.wallpaper_remove_key)) }
            }
        }
    }
}
