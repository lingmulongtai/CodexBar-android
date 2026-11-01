package com.codexbar.android.feature.github

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.BuildConfig
import com.codexbar.android.R
import com.codexbar.android.LocalTopLevelSwipeEnabled
import com.codexbar.android.core.domain.model.GitHubWidgetKind
import com.codexbar.android.core.domain.model.GitHubWidgetSize
import com.codexbar.android.core.network.github.GitHubContributionDay
import com.codexbar.android.core.network.github.GitHubProfile
import com.codexbar.android.core.network.github.GitHubRepository
import com.codexbar.android.core.network.github.GitHubRepositoryActivity
import com.codexbar.android.ui.components.*

private const val GITHUB_DEVICE_URL = "https://github.com/login/device"

private enum class GitHubPage { Profile, Repositories, Starred, Widgets }
internal enum class RepositorySort { Updated, Pushed, Stars, Name, Commits, Contributions }

@Composable
internal fun GitHubDashboardScreen(viewModel: GitHubDashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var browserError by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(GitHubPage.Profile) }
    var sort by rememberSaveable { mutableStateOf(RepositorySort.Updated) }
    var selectedDay by remember { mutableStateOf<GitHubContributionDay?>(null) }
    val swipeEnabled = LocalTopLevelSwipeEnabled.current
    DisposableEffect(page, swipeEnabled) {
        if (page == GitHubPage.Widgets) swipeEnabled?.value = false
        onDispose { swipeEnabled?.value = true }
    }
    LaunchedEffect(state.profile) { if (state.profile == null) page = GitHubPage.Profile }
    fun open(url: String) { browserError = runCatching { uri.openUri(url) }.isFailure }
    BackHandler(enabled = page != GitHubPage.Profile) { page = GitHubPage.Profile }
    val title = when (page) {
        GitHubPage.Profile -> stringResource(R.string.github_dashboard_title)
        GitHubPage.Repositories -> stringResource(R.string.github_profile_repositories)
        GitHubPage.Starred -> stringResource(R.string.github_profile_starred)
        GitHubPage.Widgets -> stringResource(R.string.github_widgets_title)
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            selectedDay = null
        }
    }) {
    GlassHeaderOverlay(header = {
        ThemedTopBar(title = title, navigationIcon = {
            if (page != GitHubPage.Profile) CodexBarIconButton(onClick = { page = GitHubPage.Profile }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        })
    }) { headerHeight ->
        if (page == GitHubPage.Profile || state.profile == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = headerHeight + 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val profile = state.profile
                if (profile == null) {
                    GitHubSignInCard(state, viewModel, ::open, browserError)
                } else {
                    val widgets = state.layout?.widgets.orEmpty()
                    val rows = remember(widgets) {
                        val result = mutableListOf<List<com.codexbar.android.core.domain.model.GitHubWidget>>()
                        var i = 0
                        while (i < widgets.size) {
                            val first = widgets[i++]
                            val second = widgets.getOrNull(i)
                            if (first.kind == GitHubWidgetKind.REPOSITORY && first.size == GitHubWidgetSize.SMALL &&
                                second?.kind == GitHubWidgetKind.REPOSITORY && second.size == GitHubWidgetSize.SMALL) {
                                result += listOf(first, second); i++
                            } else result += listOf(first)
                        }
                        result
                    }
                    rows.forEach { row ->
                        if (row.size == 2) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { widget ->
                                    Box(Modifier.weight(1f)) {
                                        state.repositories.find { it.id == widget.repositoryId }?.let { GitHubPinnedRepository(it, widget.size, ::open) }
                                    }
                                }
                            }
                        } else {
                        val widget = row.first()
                        when (widget.kind) {
                            GitHubWidgetKind.PROFILE -> GitHubProfileCard(profile, state,
                                onRepositories = { sort = RepositorySort.Updated; page = GitHubPage.Repositories },
                                onStarred = { sort = RepositorySort.Updated; page = GitHubPage.Starred })
                            GitHubWidgetKind.COMMITS -> {
                                GitHubContributionCards(state.contributions, state.contributionsUnavailable, state.loading,
                                    selectedDay = selectedDay, onSelectDay = { selectedDay = it }, kind = widget.kind, size = widget.size)
                                if (!state.privateContributionsConnected) GitHubContributionsLogin(state, viewModel, ::open)
                            }
                            GitHubWidgetKind.CALENDAR, GitHubWidgetKind.ACTIVITY ->
                                GitHubContributionCards(state.contributions, state.contributionsUnavailable, state.loading,
                                    selectedDay = selectedDay, onSelectDay = { selectedDay = it }, kind = widget.kind, size = widget.size)
                            GitHubWidgetKind.REPOSITORY -> {
                                val repo = state.repositories.find { it.id == widget.repositoryId }
                                if (repo != null) Box(if (widget.size == GitHubWidgetSize.SMALL) Modifier.fillMaxWidth(0.5f) else Modifier.fillMaxWidth()) {
                                    GitHubPinnedRepository(repo, widget.size, ::open)
                                }
                            }
                        }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CodexBarOutlinedButton(onClick = viewModel::signOut) { Text(stringResource(R.string.github_dashboard_sign_out)) }
                        CodexBarIconButton(onClick = { page = GitHubPage.Widgets }) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.github_widgets_title))
                        }
                    }
                    if (state.error != null || browserError) GitHubStatus(state, browserError)
                }
                WallpaperFooter()
                Spacer(Modifier.height(LocalBottomChromeInset.current + 32.dp))
            }
        } else if (page == GitHubPage.Widgets) {
            GitHubWidgetEditor(state = state, headerHeight = headerHeight, onChange = viewModel::updateWidgets)
        } else {
            val entries = if (page == GitHubPage.Starred) state.starred else state.repositories
            val shown = remember(entries, sort, state.repositoryActivity) {
                sortRepositories(entries.orEmpty(), sort, state.repositoryActivity)
            }
            LazyColumn(modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeight + 8.dp,
                    bottom = LocalBottomChromeInset.current + 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.github_profile_repo_sort), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RepositorySort.entries.forEach { option ->
                                CodexBarFilterChip(selected = sort == option, onClick = { sort = option },
                                    label = { Text(stringResource(option.label())) })
                            }
                        }
                        if (sort == RepositorySort.Commits || sort == RepositorySort.Contributions) {
                            Text(stringResource(R.string.github_profile_sort_scope), style = MaterialTheme.typography.bodySmall)
                            if (state.repositoryActivity?.complete != true)
                                Text(stringResource(R.string.github_profile_sort_incomplete), style = MaterialTheme.typography.bodySmall)
                        }
                        if (page == GitHubPage.Repositories && !state.repositoriesComplete)
                            Text(stringResource(R.string.github_profile_repo_incomplete), style = MaterialTheme.typography.bodySmall)
                        if (page == GitHubPage.Starred && entries != null && !state.starredComplete)
                            Text(stringResource(R.string.github_profile_repo_incomplete), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (entries == null) item { Text(stringResource(R.string.github_dashboard_unavailable)) }
                else if (shown.isEmpty()) item {
                    Text(stringResource(if (page == GitHubPage.Starred) R.string.github_profile_no_stars else R.string.github_dashboard_no_repos))
                }
                items(shown, key = { it.id }) { repo ->
                    GitHubRepositoryCard(repo, state.repositoryActivity, ::open)
                }
                item { WallpaperFooter() }
            }
        }
    }
    }
}

@Composable
private fun GitHubContributionsLogin(state: GitHubDashboardState, viewModel: GitHubDashboardViewModel,
    open: (String) -> Unit) {
    var clientId by remember { mutableStateOf(BuildConfig.GITHUB_CONTRIBUTIONS_CLIENT_ID) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (BuildConfig.GITHUB_CONTRIBUTIONS_CLIENT_ID.isBlank()) CodexBarOutlinedTextField(
            value = clientId, onValueChange = { clientId = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.github_contributions_client_id)) }, singleLine = true)
        if (state.contributionsUserCode != null) {
            Text(state.contributionsUserCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.github_dashboard_verify_notice), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CodexBarButton(onClick = { open(GITHUB_DEVICE_URL) }) { Text(stringResource(R.string.github_dashboard_open_github)) }
                CodexBarOutlinedButton(onClick = viewModel::cancelContributionsLogin) { Text(stringResource(R.string.github_dashboard_cancel)) }
            }
        } else {
            CodexBarOutlinedButton(onClick = { viewModel.signInContributions(clientId.trim()) },
                enabled = !state.contributionsLoginLoading && clientId.isNotBlank()) {
                Text(stringResource(R.string.github_contributions_connect))
            }
        }
        if (state.contributionsLoginError != null) Text(stringResource(R.string.github_contributions_error),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GitHubSignInCard(state: GitHubDashboardState, viewModel: GitHubDashboardViewModel,
    open: (String) -> Unit, browserError: Boolean) {
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.github_dashboard_connect_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.github_dashboard_connect_description), style = MaterialTheme.typography.bodyMedium)
            if (state.userCode == null) {
                CodexBarButton(onClick = viewModel::signIn, enabled = !state.loading) { Text(stringResource(R.string.github_dashboard_sign_in)) }
                if (state.hasStoredSession) CodexBarOutlinedButton(onClick = viewModel::signOut, enabled = !state.loading) {
                    Text(stringResource(R.string.github_dashboard_sign_out))
                }
            } else {
                Text(stringResource(R.string.github_dashboard_enter_code), style = MaterialTheme.typography.bodyMedium)
                Text(state.userCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.github_dashboard_verify_notice), style = MaterialTheme.typography.bodySmall)
                CodexBarButton(onClick = { open(GITHUB_DEVICE_URL) }) { Text(stringResource(R.string.github_dashboard_open_github)) }
                CodexBarOutlinedButton(onClick = viewModel::signOut) { Text(stringResource(R.string.github_dashboard_cancel)) }
            }
            GitHubStatus(state, browserError)
        }
    }
}

@Composable
private fun GitHubProfileCard(profile: GitHubProfile, state: GitHubDashboardState,
    onRepositories: () -> Unit, onStarred: () -> Unit) {
    val privateCount = state.repositories.count { it.isPrivate && it.fullName.substringBefore('/').equals(profile.login, true) }
    val privateLabel = (if (state.repositoriesComplete) "" else "≥") + privateCount
    val starLabel = state.starred?.size?.let { (if (state.starredComplete) "" else "≥") + it } ?: "—"
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GitHubAvatar(profile.avatarUrl, profile.login)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(profile.name?.takeIf { it.isNotBlank() } ?: profile.login,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@" + profile.login, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.contributions?.viewer?.isDeveloperProgramMember == true)
                        Text(stringResource(R.string.github_profile_developer), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CodexBarIconButton(onClick = onRepositories) {
                    Icon(Icons.Rounded.Folder, contentDescription = stringResource(R.string.github_profile_repositories))
                }
                CodexBarIconButton(onClick = onStarred) {
                    Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.github_profile_starred))
                }
            }
            profile.bio?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.github_profile_followers, profile.followers), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.github_profile_following, profile.following), style = MaterialTheme.typography.labelLarge)
            }
            profile.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.github_dashboard_public_count, profile.publicRepos) + "  ·  " +
                stringResource(R.string.github_profile_private_count, privateLabel), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.github_profile_star_count, starLabel), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GitHubStatus(state: GitHubDashboardState, browserError: Boolean) {
    if (state.loading && state.userCode == null) Text(stringResource(R.string.github_profile_loading_repos), style = MaterialTheme.typography.bodySmall)
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

private fun RepositorySort.label(): Int = when (this) {
    RepositorySort.Updated -> R.string.github_profile_sort_updated
    RepositorySort.Pushed -> R.string.github_profile_sort_pushed
    RepositorySort.Stars -> R.string.github_profile_sort_stars
    RepositorySort.Name -> R.string.github_profile_sort_name
    RepositorySort.Commits -> R.string.github_profile_sort_commits
    RepositorySort.Contributions -> R.string.github_profile_sort_contributions
}

private fun repositoryCounts(repo: GitHubRepository, activity: GitHubRepositoryActivity?) =
    activity?.counts?.get(repo.fullName) ?: if (activity?.complete == true && !repo.isPrivate)
        com.codexbar.android.core.network.github.GitHubRepositoryCounts(0, 0) else null

internal fun sortRepositories(repos: List<GitHubRepository>, sort: RepositorySort, activity: GitHubRepositoryActivity?): List<GitHubRepository> {
    val byName = compareBy<GitHubRepository> { it.fullName.lowercase() }
    return when (sort) {
        RepositorySort.Updated -> repos.sortedWith(compareByDescending<GitHubRepository> { it.updatedAt ?: "" }.then(byName))
        RepositorySort.Pushed -> repos.sortedWith(compareByDescending<GitHubRepository> { it.pushedAt ?: "" }.then(byName))
        RepositorySort.Stars -> repos.sortedWith(compareByDescending<GitHubRepository> { it.stars }.then(byName))
        RepositorySort.Name -> repos.sortedWith(byName)
        RepositorySort.Commits, RepositorySort.Contributions -> repos.sortedWith(
            compareByDescending<GitHubRepository> {
                val counts = repositoryCounts(it, activity)
                (if (sort == RepositorySort.Commits) counts?.commits else counts?.contributions) ?: -1
            }.then(byName))
    }
}

@Composable
private fun GitHubRepositoryCard(repo: GitHubRepository, activity: GitHubRepositoryActivity?, open: (String) -> Unit) {
    val counts = repositoryCounts(repo, activity)
    CodexBarCard(onClick = { open(repo.htmlUrl) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(repo.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.github_profile_repo_stars, repo.stars), style = MaterialTheme.typography.labelMedium)
            }
            Text(stringResource(if (repo.isPrivate) R.string.github_dashboard_private else R.string.github_dashboard_public) +
                (repo.language?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            repo.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (counts?.commits != null && counts.contributions != null)
                Text(stringResource(R.string.github_profile_repo_activity, counts.commits, counts.contributions), style = MaterialTheme.typography.bodySmall)
        }
    }
}
