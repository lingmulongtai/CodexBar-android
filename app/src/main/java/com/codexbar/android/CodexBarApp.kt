package com.codexbar.android

import androidx.annotation.StringRes
import com.codexbar.android.ui.components.WallpaperAttribution
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Tune
import com.codexbar.android.feature.dashboard.UsageAnalyticsPage
import com.codexbar.android.feature.dashboard.UsageAnalyticsScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.dashboard.DashboardPreviewScreen
import com.codexbar.android.feature.dashboard.DashboardViewModel
import com.codexbar.android.feature.settings.ConnectionsScreen
import com.codexbar.android.feature.settings.SettingsScreen
import com.codexbar.android.feature.settings.SettingsViewModel
import com.codexbar.android.core.domain.model.AppThemeStyle
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
    Settings(
        route = SettingsRoute,
        labelRes = R.string.settings_title,
        icon = Icons.Rounded.Tune
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
    var initialDestinationHandled by rememberSaveable { mutableStateOf(false) }
    var lastTrendsRoute by rememberSaveable { mutableStateOf(HistoryRoute) }

    LaunchedEffect(currentRoute) {
        if (currentRoute in AppDestination.Trends.routes) lastTrendsRoute = currentRoute
    }

    LaunchedEffect(initialDestination, initialDestinationHandled) {
        if (!initialDestinationHandled) {
            initialDestinationHandled = true
            if (initialDestination in setOf(ConnectionsRoute, SettingsRoute, HistoryRoute, SpendRoute)) {
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
                val route = if (destination == AppDestination.Trends) lastTrendsRoute else destination.route
                navController.navigateTopLevel(route)
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
            dashboardPreviewSnapshot = dashboardPreviewSnapshot,
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
                val refresh = dashboardViewModel?.refreshState?.collectAsStateWithLifecycle()?.value
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
                Box(Modifier.fillMaxSize().glassBackdropSource(cardBackdrop)) {
                    LiquidGlassWallpaper(wallpaper)
                }
                val attributionHeight = if (wallpaper.preferences.enabled && wallpaper.bitmap != null) with(LocalDensity.current) { 18.sp.toDp() } + 12.dp else 0.dp
                val bottomInset = attributionHeight + GlassBarHeight + GlassBarMargin * 2 +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                CompositionLocalProvider(
                    LocalBottomChromeInset provides bottomInset,
                    LocalGlassCardBackdrop provides cardBackdrop
                ) {
                    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
                        LiquidGlassWallpaper(wallpaper)
                        navHost(Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter)) {
                    WallpaperAttribution(wallpaper, Modifier.height(attributionHeight))
                GlassNavigationBar(
                    entries = entries,
                    backdrop = backdrop,
                    refresh = refresh,
                    onRefresh = dashboardViewModel?.let { model -> { model.refresh() } },
                    modifier = Modifier
                )
                }
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
    dashboardPreviewSnapshot: QuotaPresentationSnapshot?,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier
    ) {
        composable(DashboardRoute) {
            val navigateToConnections = {
                navController.navigateTopLevel(ConnectionsRoute)
            }
            if (dashboardViewModel != null) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToConnections = navigateToConnections,
                    initialSelectedService = initialDashboardService,
                    onInitialSelectionConsumed = onDashboardServiceConsumed
                )
            } else if (dashboardPreviewSnapshot != null) {
                DashboardPreviewScreen(
                    snapshot = dashboardPreviewSnapshot,
                    onNavigateToConnections = navigateToConnections
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
        val showTrendsPage = { page: UsageAnalyticsPage ->
            navController.navigateTopLevel(if (page == UsageAnalyticsPage.History) HistoryRoute else SpendRoute)
        }
        composable(HistoryRoute) {
            UsageAnalyticsScreen(
                page = UsageAnalyticsPage.History,
                onConnections = { navController.navigateTopLevel(ConnectionsRoute) },
                preview = dashboardPreviewSnapshot,
                model = dashboardViewModel,
                onPageChange = showTrendsPage
            )
        }
        composable(SpendRoute) {
            UsageAnalyticsScreen(
                page = UsageAnalyticsPage.Spend,
                onConnections = { navController.navigateTopLevel(ConnectionsRoute) },
                preview = dashboardPreviewSnapshot,
                model = dashboardViewModel,
                onPageChange = showTrendsPage
            )
        }
        composable(SettingsRoute) {
            SettingsScreen(
                onNavigateBack = {
                    navController.navigateTopLevel(DashboardRoute)
                },
                showBackButton = false,
                onScreenPrivacyChanged = onScreenPrivacyChanged,
                viewModel = settingsViewModel
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
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
