package com.codexbar.android.feature.github

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.ui.components.*

private const val GITHUB_DEVICE_URL = "https://github.com/login/device"
private const val GITHUB_INSTALL_URL = "https://github.com/apps/codexbar-android-dashboard/installations/new"

@Composable
internal fun GitHubDashboardScreen(viewModel: GitHubDashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var browserError by remember { mutableStateOf(false) }
    fun open(url: String) { browserError = runCatching { uri.openUri(url) }.isFailure }
    GlassHeaderOverlay(header = { ThemedTopBar(title = stringResource(R.string.github_dashboard_title)) }) { headerHeight ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = headerHeight + 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CodexBarCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val profile = state.profile
                    if (profile == null) {
                        Text(stringResource(R.string.github_dashboard_connect_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.github_dashboard_connect_description), style = MaterialTheme.typography.bodyMedium)
                        if (state.userCode == null) {
                            CodexBarButton(onClick = viewModel::signIn, enabled = !state.loading) {
                                Text(stringResource(R.string.github_dashboard_sign_in))
                            }
                        } else {
                            Text(stringResource(R.string.github_dashboard_enter_code), style = MaterialTheme.typography.bodyMedium)
                            Text(state.userCode.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.github_dashboard_verify_notice), style = MaterialTheme.typography.bodySmall)
                            CodexBarButton(onClick = { open(GITHUB_DEVICE_URL) }) { Text(stringResource(R.string.github_dashboard_open_github)) }
                            CodexBarOutlinedButton(onClick = viewModel::signOut) { Text(stringResource(R.string.github_dashboard_cancel)) }
                        }
                    } else {
                        Text(profile.name ?: profile.login, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("@" + profile.login, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.github_dashboard_public_count, profile.publicRepos), style = MaterialTheme.typography.bodySmall)
                        CodexBarTextButton(onClick = { open(GITHUB_INSTALL_URL) }) { Text(stringResource(R.string.github_dashboard_manage_access)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CodexBarOutlinedButton(onClick = viewModel::refresh, enabled = !state.loading) { Text(stringResource(R.string.action_refresh)) }
                            CodexBarOutlinedButton(onClick = viewModel::signOut) { Text(stringResource(R.string.github_dashboard_sign_out)) }
                        }
                    }
                    if (state.loading && state.userCode == null) Text(stringResource(R.string.github_dashboard_loading), style = MaterialTheme.typography.bodySmall)
                    state.error?.let { error ->
                        Text(stringResource(when (error) {
                            GitHubDashboardError.Unavailable -> R.string.github_dashboard_unavailable
                            GitHubDashboardError.Unauthorized -> R.string.github_dashboard_unauthorized
                            GitHubDashboardError.RateLimited -> R.string.github_dashboard_rate_limited
                            GitHubDashboardError.Expired -> R.string.github_dashboard_expired
                            GitHubDashboardError.InvalidResponse -> R.string.github_dashboard_invalid
                        }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (browserError) Text(stringResource(R.string.app_info_browser_unavailable), color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.profile != null) {
                Text(stringResource(R.string.github_dashboard_recent_repos), style = MaterialTheme.typography.titleMedium)
                if (state.repositories.isEmpty()) Text(stringResource(R.string.github_dashboard_no_repos), style = MaterialTheme.typography.bodyMedium)
                state.repositories.forEach { repo ->
                    CodexBarCard(onClick = { open(repo.htmlUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(repo.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(if (repo.isPrivate) R.string.github_dashboard_private else R.string.github_dashboard_public), style = MaterialTheme.typography.labelMedium)
                            repo.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3) }
                        }
                    }
                }
            }
            WallpaperFooter()
            Spacer(Modifier.height(LocalBottomChromeInset.current + 32.dp))
        }
    }
}
