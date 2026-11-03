package com.codexbar.android

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.feature.dashboard.*
import com.codexbar.android.feature.github.GitHubDashboardScreen
import com.codexbar.android.feature.github.GitHubDashboardViewModel
import com.codexbar.android.feature.settings.*
import com.codexbar.android.ui.components.*
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DashboardRoute = "dashboard"
private const val ConnectionsRoute = "connections"
private const val HistoryRoute = "history"
private const val SpendRoute = "spend"
private const val SettingsRoute = "settings"
private const val DeveloperSettingsRoute = "developer-settings"
private const val GitHubRoute = "github"
private const val ExpandedNavigationMinWidthDp = 600f

private enum class AppDestination(
    val route: String, @param:StringRes val labelRes: Int, val icon: ImageVector
) {
    Dashboard(DashboardRoute, R.string.navigation_dashboard, Icons.Rounded.Speed),
    Trends(HistoryRoute, R.string.navigation_trends, Icons.AutoMirrored.Rounded.ShowChart),
    Connections(ConnectionsRoute, R.string.connections_title, Icons.Rounded.Link),
    GitHub(GitHubRoute, R.string.github_dashboard_title, Icons.Rounded.Code)
}

@Composable
fun CodexBarApp(
    initialDestination: String,
    initialGeminiPairingUri: String? = null,
    onGeminiPairingConsumed: () -> Unit = {},
    initialCodexTelemetryPairingUri: String? = null,
    onCodexTelemetryPairingConsumed: () -> Unit = {},
    initialDashboardService: AiService? = null,
    onDashboardServiceConsumed: () -> Unit = {},
    dashboardPreviewSnapshot: QuotaPresentationSnapshot? = null,
    onScreenPrivacyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeProfile = LocalCodexBarThemeProfile.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val dashboardViewModel: DashboardViewModel? = if (dashboardPreviewSnapshot == null) hiltViewModel() else null
    val githubViewModel: GitHubDashboardViewModel = hiltViewModel()
    val configuration by settingsViewModel.uiState.collectAsStateWithLifecycle()
    ResetNotificationPermissionSetup(enabled = dashboardPreviewSnapshot == null && configuration.preferencesLoaded &&
        configuration.resetNotificationEnabled && (configuration.desktop.services.isNotEmpty() || configuration.serviceStates.values.any { it.isConnected }))

    val initial = initialPageLocation(initialDestination)
    val tabs = rememberPagerState(initialPage = initial.tab) { AppDestination.entries.size }
    val panes = rememberPagerState(initialPage = initial.pane) { 3 }
    val scope = rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    var navigationRequest by remember { mutableStateOf<Pair<Long, PageLocation>?>(null) }
    var navigationGeneration by remember { mutableLongStateOf(0L) }
    fun navigate(location: PageLocation) {
        navigation?.cancel()
        val request = ++navigationGeneration to location
        navigationRequest = request
        navigation = scope.launch {
            try {
                coroutineScope {
                    launch { tabs.animateContinuouslyToPage(location.tab) }
                    launch { panes.animateContinuouslyToPage(location.pane) }
                }
            } finally {
                if (navigationRequest?.first == request.first) navigationRequest = null
            }
        }
    }
    val back = backPageLocation(PageLocation(tabs.currentPage, panes.currentPage),
        navigationRequest?.second, tabs.isScrollInProgress || panes.isScrollInProgress)
    BackHandler(enabled = back != null) { back?.let(::navigate) }

    LaunchedEffect(initialGeminiPairingUri, initialCodexTelemetryPairingUri) {
        if (initialGeminiPairingUri != null || initialCodexTelemetryPairingUri != null) navigate(PageLocation(tab = 2))
    }
    LaunchedEffect(initialDashboardService) { if (initialDashboardService != null) navigate(PageLocation()) }

    var lastTrendsRoute by rememberSaveable { mutableStateOf(if (initialDestination == SpendRoute) SpendRoute else HistoryRoute) }
    val savedPages = rememberSaveableStateHolder()
    val headers = remember { List(6) { PinnedGlassHeader() } }
    val swipeEnabled = remember { List(6) { mutableStateOf(true) } }
    val headerHeight = remember { Animatable(0f) }
    val activeHeader = if (panes.currentPage == 0) tabs.currentPage else panes.currentPage + 3
    val entries = AppDestination.entries.mapIndexed { index, destination ->
        NavigationEntry(stringResource(destination.labelRes), destination.icon, tabs.currentPage == index,
            onClick = { navigate(PageLocation(tab = index)) })
    }

    val pages: @Composable (Modifier) -> Unit = { hostModifier ->
        CompositionLocalProvider(LocalGlassHeaderHeight provides headerHeight) {
            Box(hostModifier) {
                PinnedGlassHeaderLayout(headers[activeHeader]) {
                    @Composable fun screen(index: Int, active: Boolean) {
                        savedPages.SaveableStateProvider(index) {
                            CompositionLocalProvider(LocalPinnedGlassHeader provides headers[index],
                                LocalPageActive provides active, LocalTopLevelSwipeEnabled provides swipeEnabled[index]) {
                                AppPage(index, settingsViewModel, dashboardViewModel, githubViewModel,
                                    dashboardPreviewSnapshot, initialDashboardService.takeIf { active }, onDashboardServiceConsumed,
                                    initialGeminiPairingUri.takeIf { active }, onGeminiPairingConsumed,
                                    initialCodexTelemetryPairingUri.takeIf { active }, onCodexTelemetryPairingConsumed,
                                    onScreenPrivacyChanged,
                                    if (lastTrendsRoute == SpendRoute) UsageAnalyticsPage.Spend else UsageAnalyticsPage.History,
                                    { lastTrendsRoute = if (it == UsageAnalyticsPage.Spend) SpendRoute else HistoryRoute },
                                    { navigate(PageLocation(tab = 2)) },
                                    { navigate(PageLocation(tabs.currentPage, 1)) },
                                    { navigate(PageLocation(tabs.currentPage, 2)) },
                                    { navigate(PageLocation(tabs.currentPage, if (index == 5) 1 else 0)) })
                            }
                        }
                    }
                    HorizontalPager(panes, Modifier.fillMaxSize(), userScrollEnabled = false,
                        beyondViewportPageCount = 1, key = { if (it == 0) "tabs" else if (it == 1) SettingsRoute else DeveloperSettingsRoute }) { pane ->
                        if (pane == 0) {
                            HorizontalPager(tabs, Modifier.fillMaxSize(), beyondViewportPageCount = 1,
                                userScrollEnabled = panes.settledPage == 0 && swipeEnabled[tabs.currentPage].value,
                                key = { AppDestination.entries[it].route }) { tab ->
                                screen(tab, panes.settledPage == 0 && !panes.isScrollInProgress &&
                                    tabs.settledPage == tab && !tabs.isScrollInProgress)
                            }
                        } else screen(pane + 3, panes.settledPage == pane && !panes.isScrollInProgress)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(themeProfile.backgroundBrush)) {
        when {
            themeProfile.floatingNavigation -> {
                val wallpaperModel: DailyWallpaperViewModel = hiltViewModel()
                val wallpaper by wallpaperModel.state.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner, wallpaperModel) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (isActive) {
                            wallpaperModel.refresh()
                            val midnight = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                            delay(java.time.Duration.between(java.time.Instant.now(), midnight).toMillis().coerceIn(1_000L, 30 * 60_000L))
                        }
                    }
                }
                val backdrop = rememberGlassBackdrop()
                val cardBackdrop = rememberGlassBackdrop()
                Box(Modifier.fillMaxSize().glassBackdropSource(cardBackdrop)) { LiquidGlassWallpaper(wallpaper) }
                val bottomInset = GlassBarHeight + GlassBarMargin * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                CompositionLocalProvider(LocalBottomChromeInset provides bottomInset,
                    LocalGlassCardBackdrop provides cardBackdrop, LocalWallpaperState provides wallpaper) {
                    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
                        LiquidGlassWallpaper(wallpaper)
                        pages(Modifier.fillMaxSize())
                    }
                }
                GlassNavigationBar(entries, backdrop,
                    onRefresh = if (tabs.currentPage == 3 && panes.currentPage == 0) ({ githubViewModel.refresh() })
                        else dashboardViewModel?.let { model -> { model.refresh() } },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    pagePosition = { tabs.currentPage + tabs.currentPageOffsetFraction })
            }
            useExpandedNavigation(maxWidth.value) -> Row(Modifier.fillMaxSize()) {
                AppNavigationRail(entries)
                VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                pages(Modifier.weight(1f).fillMaxHeight())
            }
            else -> Column(Modifier.fillMaxSize()) {
                pages(Modifier.weight(1f).fillMaxWidth())
                AppBottomBar(entries)
            }
        }
    }
}

@Composable
private fun AppPage(
    page: Int, settingsViewModel: SettingsViewModel, dashboardViewModel: DashboardViewModel?,
    githubViewModel: GitHubDashboardViewModel, dashboardPreviewSnapshot: QuotaPresentationSnapshot?,
    initialDashboardService: AiService?, onDashboardServiceConsumed: () -> Unit,
    initialGeminiPairingUri: String?, onGeminiPairingConsumed: () -> Unit,
    initialCodexTelemetryPairingUri: String?, onCodexTelemetryPairingConsumed: () -> Unit,
    onScreenPrivacyChanged: (Boolean) -> Unit, trendsPage: UsageAnalyticsPage,
    onTrendsPageChange: (UsageAnalyticsPage) -> Unit, onConnections: () -> Unit,
    onSettings: () -> Unit, onDeveloperSettings: () -> Unit, onBack: () -> Unit
) {
    when (page) {
        0 -> if (dashboardViewModel != null) DashboardScreen(viewModel = dashboardViewModel,
            onNavigateToConnections = onConnections, onNavigateToSettings = onSettings,
            initialSelectedService = initialDashboardService, onInitialSelectionConsumed = onDashboardServiceConsumed)
        else if (dashboardPreviewSnapshot != null) DashboardPreviewScreen(dashboardPreviewSnapshot,
            onNavigateToConnections = onConnections, onNavigateToSettings = onSettings)
        1 -> UsageAnalyticsScreen(trendsPage, onConnections, dashboardPreviewSnapshot, dashboardViewModel, onTrendsPageChange, onSettings)
        2 -> ConnectionsScreen(onNavigateBack = onBack, showBackButton = false,
            initialGeminiPairingUri = initialGeminiPairingUri, onGeminiPairingConsumed = onGeminiPairingConsumed,
            initialCodexTelemetryPairingUri = initialCodexTelemetryPairingUri, onCodexTelemetryPairingConsumed = onCodexTelemetryPairingConsumed,
            viewModel = settingsViewModel, onOpenSettings = onSettings)
        3 -> GitHubDashboardScreen(viewModel = githubViewModel, onOpenSettings = onSettings)
        4 -> SettingsScreen(onNavigateBack = onBack, showBackButton = true, onScreenPrivacyChanged = onScreenPrivacyChanged,
            onOpenDeveloperSettings = onDeveloperSettings, viewModel = settingsViewModel)
        5 -> DeveloperSettingsScreen(onBack = onBack, viewModel = settingsViewModel)
    }
}

internal fun useExpandedNavigation(widthDp: Float): Boolean = widthDp >= ExpandedNavigationMinWidthDp
