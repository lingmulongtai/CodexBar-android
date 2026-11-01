package com.codexbar.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.annotation.StringRes
import com.codexbar.android.ui.components.LocalWallpaperState
import androidx.compose.ui.unit.dp
import com.codexbar.android.ui.components.LiquidGlassWallpaper
import com.codexbar.android.ui.components.DailyWallpaperViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Code
import com.codexbar.android.feature.dashboard.UsageAnalyticsPage
import com.codexbar.android.feature.dashboard.UsageAnalyticsScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.ui.components.ResetNotificationPermissionSetup
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.dashboard.DashboardPreviewScreen
import com.codexbar.android.feature.dashboard.DashboardViewModel
import com.codexbar.android.feature.github.GitHubDashboardScreen
import com.codexbar.android.feature.github.GitHubDashboardViewModel
import com.codexbar.android.feature.settings.ConnectionsScreen
import com.codexbar.android.feature.settings.SettingsScreen
import com.codexbar.android.feature.settings.SettingsViewModel
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.components.LocalGlassHeaderHeight
import com.codexbar.android.ui.components.LocalTopLevelSlideDirection
import com.codexbar.android.ui.components.LocalBottomChromeInset
import com.codexbar.android.ui.components.LocalGlassCardBackdrop
import com.codexbar.android.ui.components.glassBackdropSource
import com.codexbar.android.ui.components.rememberGlassBackdrop
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot

private const val DashboardRoute = "dashboard"
private const val ConnectionsRoute = "connections"
private const val HistoryRoute = "history"
private const val SpendRoute = "spend"
private const val SettingsRoute = "settings"
private const val GitHubRoute = "github"
private const val ExpandedNavigationMinWidthDp = 600f

private enum class AppDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val routes: Set<String> = setOf(route)
) {
    Dashboard(
        route = DashboardRoute,
        labelRes = R.string.navigation_dashboard,
        icon = Icons.Rounded.Speed
    ),
    // History and Spend share one tab and switch with the tabs at the top of the screen.
    Trends(
        route = HistoryRoute,
        labelRes = R.string.navigation_trends,
        icon = Icons.AutoMirrored.Rounded.ShowChart,
        routes = setOf(HistoryRoute, SpendRoute)
    ),
    Connections(
        route = ConnectionsRoute,
        labelRes = R.string.connections_title,
        icon = Icons.Rounded.Link
    ),
    GitHub(
        route = GitHubRoute,
        labelRes = R.string.github_dashboard_title,
        icon = Icons.Rounded.Code
    )
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
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    // One dashboard model for every tab, so refresh progress and readings are shared.
    val dashboardViewModel: DashboardViewModel? =
        if (dashboardPreviewSnapshot == null) hiltViewModel() else null
    val configuration by settingsViewModel.uiState.collectAsStateWithLifecycle()
    ResetNotificationPermissionSetup(enabled = dashboardPreviewSnapshot == null && configuration.preferencesLoaded &&
        configuration.resetNotificationEnabled && (configuration.desktop.services.isNotEmpty() || configuration.serviceStates.values.any { it.isConnected }))
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: initialDestination
    val githubViewModel: GitHubDashboardViewModel = hiltViewModel()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var slideDirection by remember { mutableStateOf(0) }
    val topLevelSwipeEnabled = remember { mutableStateOf(true) }
    DisposableEffect(navController, rtl) {
        var previousRoute = navController.currentDestination?.route
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            slideDirection = topLevelSlideDirection(previousRoute, destination.route, rtl)
            previousRoute = destination.route
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    var initialDestinationHandled by rememberSaveable { mutableStateOf(false) }
    var lastTrendsRoute by rememberSaveable {
        mutableStateOf(if (initialDestination == SpendRoute) SpendRoute else HistoryRoute)
    }

    LaunchedEffect(initialDestination, initialDestinationHandled) {
        if (!initialDestinationHandled) {
            initialDestinationHandled = true
            if (initialDestination in setOf(ConnectionsRoute, SettingsRoute, HistoryRoute, SpendRoute, GitHubRoute)) {
                navController.navigate(initialDestination) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(initialGeminiPairingUri) {
        if (initialGeminiPairingUri != null) {
            navController.navigate(ConnectionsRoute) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(initialCodexTelemetryPairingUri) {
        if (initialCodexTelemetryPairingUri != null) {
            navController.navigate(ConnectionsRoute) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(initialDashboardService) {
        if (initialDashboardService != null) {
            navController.navigateTopLevel(DashboardRoute)
        }
    }

    val entries = AppDestination.entries.map { destination ->
        NavigationEntry(
            label = stringResource(destination.labelRes),
            icon = destination.icon,
            selected = currentRoute in destination.routes,
            onClick = {
                navController.navigateTopLevel(destination.route)
            }
        )
    }
    val navHost: @Composable (Modifier) -> Unit = { hostModifier ->
        AppNavHost(
            navController = navController,
            initialGeminiPairingUri = initialGeminiPairingUri,
            onGeminiPairingConsumed = onGeminiPairingConsumed,
            initialCodexTelemetryPairingUri = initialCodexTelemetryPairingUri,
            onCodexTelemetryPairingConsumed = onCodexTelemetryPairingConsumed,
            initialDashboardService = initialDashboardService,
            onDashboardServiceConsumed = onDashboardServiceConsumed,
            onScreenPrivacyChanged = onScreenPrivacyChanged,
            settingsViewModel = settingsViewModel,
            dashboardViewModel = dashboardViewModel,
            githubViewModel = githubViewModel,
            dashboardPreviewSnapshot = dashboardPreviewSnapshot,
            trendsPage = if (lastTrendsRoute == SpendRoute) UsageAnalyticsPage.Spend else UsageAnalyticsPage.History,
            onTrendsPageChange = { page -> lastTrendsRoute = if (page == UsageAnalyticsPage.Spend) SpendRoute else HistoryRoute },
            modifier = hostModifier
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(themeProfile.backgroundBrush)
    ) {
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
                val headerHeight = remember { Animatable(0f) }
                Box(Modifier.fillMaxSize().glassBackdropSource(cardBackdrop)) {
                    LiquidGlassWallpaper(wallpaper)
                }
                val bottomInset = GlassBarHeight + GlassBarMargin * 2 +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                CompositionLocalProvider(
                    LocalBottomChromeInset provides bottomInset,
                    LocalTopLevelSlideDirection provides slideDirection,
                    LocalTopLevelSwipeEnabled provides topLevelSwipeEnabled,
                    LocalGlassHeaderHeight provides headerHeight,
                    LocalGlassCardBackdrop provides cardBackdrop,
                    LocalWallpaperState provides wallpaper
                ) {
                    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
                        LiquidGlassWallpaper(wallpaper)
                        navHost(Modifier.fillMaxSize().topLevelSwipe(currentRoute, rtl, topLevelSwipeEnabled.value) {
                            navController.navigateTopLevel(it)
                        })
                    }
                }
                GlassNavigationBar(
                    entries = entries,
                    backdrop = backdrop,
                    onRefresh = if (currentRoute == GitHubRoute) ({ githubViewModel.refresh() })
                        else dashboardViewModel?.let { model -> { model.refresh() } },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            useExpandedNavigation(maxWidth.value) -> Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(entries)
                VerticalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                navHost(Modifier.weight(1f).fillMaxHeight())
            }
            else -> Column(modifier = Modifier.fillMaxSize()) {
                navHost(Modifier.weight(1f).fillMaxWidth())
                AppBottomBar(entries)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    initialGeminiPairingUri: String?,
    onGeminiPairingConsumed: () -> Unit,
    initialCodexTelemetryPairingUri: String?,
    onCodexTelemetryPairingConsumed: () -> Unit,
    initialDashboardService: AiService?,
    onDashboardServiceConsumed: () -> Unit,
    onScreenPrivacyChanged: (Boolean) -> Unit,
    settingsViewModel: SettingsViewModel,
    dashboardViewModel: DashboardViewModel?,
    githubViewModel: GitHubDashboardViewModel,
    dashboardPreviewSnapshot: QuotaPresentationSnapshot?,
    trendsPage: UsageAnalyticsPage,
    onTrendsPageChange: (UsageAnalyticsPage) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier.clipToBounds(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(DashboardRoute) {
            val navigateToConnections = {
                navController.navigateTopLevel(ConnectionsRoute)
            }
            if (dashboardViewModel != null) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToConnections = navigateToConnections,
                    onNavigateToSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                    initialSelectedService = initialDashboardService,
                    onInitialSelectionConsumed = onDashboardServiceConsumed
                )
            } else if (dashboardPreviewSnapshot != null) {
                DashboardPreviewScreen(
                    snapshot = dashboardPreviewSnapshot,
                    onNavigateToConnections = navigateToConnections,
                    onNavigateToSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } }
                )
            }
        }
        composable(ConnectionsRoute) {
            ConnectionsScreen(
                onNavigateBack = {
                    navController.navigateTopLevel(DashboardRoute)
                },
                showBackButton = false,
                initialGeminiPairingUri = initialGeminiPairingUri,
                onGeminiPairingConsumed = onGeminiPairingConsumed,
                initialCodexTelemetryPairingUri = initialCodexTelemetryPairingUri,
                onCodexTelemetryPairingConsumed = onCodexTelemetryPairingConsumed,
                viewModel = settingsViewModel
            )
        }
        composable(HistoryRoute) {
            UsageAnalyticsScreen(
                page = trendsPage,
                onConnections = { navController.navigateTopLevel(ConnectionsRoute) },
                preview = dashboardPreviewSnapshot,
                model = dashboardViewModel,
                onPageChange = onTrendsPageChange
            )
        }
        composable(SpendRoute) {
            UsageAnalyticsScreen(
                page = trendsPage,
                onConnections = { navController.navigateTopLevel(ConnectionsRoute) },
                preview = dashboardPreviewSnapshot,
                model = dashboardViewModel,
                onPageChange = onTrendsPageChange
            )
        }
        composable(GitHubRoute) { GitHubDashboardScreen(viewModel = githubViewModel) }
        composable(SettingsRoute) {
            SettingsScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) navController.navigateTopLevel(DashboardRoute)
                },
                showBackButton = true,
                onScreenPrivacyChanged = onScreenPrivacyChanged,
                viewModel = settingsViewModel
            )
        }
    }
}

internal fun topLevelSlideDirection(from: String?, to: String?, rtl: Boolean): Int {
    fun index(route: String?): Int = when (route) {
        DashboardRoute -> 0
        HistoryRoute, SpendRoute -> 1
        ConnectionsRoute -> 2
        GitHubRoute -> 3
        SettingsRoute -> 4
        else -> -1
    }
    val start = index(from)
    val end = index(to)
    if (start < 0 || end < 0 || start == end) return 0
    val direction = if (end > start) 1 else -1
    return if (rtl) -direction else direction
}

private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    // Navigating to the start destination with restoreState can restore the tab that was just
    // popped instead of the dashboard. Pop back to the existing dashboard directly.
    if (route == DashboardRoute) {
        if (!popBackStack(DashboardRoute, inclusive = false, saveState = true)) {
            navigate(DashboardRoute) { launchSingleTop = true; restoreState = false }
        }
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun useExpandedNavigation(widthDp: Float): Boolean {
    return widthDp >= ExpandedNavigationMinWidthDp
}
