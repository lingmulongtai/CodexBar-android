package com.codexbar.android

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.settings.SettingsScreen

private const val DashboardRoute = "dashboard"
private const val SettingsRoute = "settings"
private const val ExpandedNavigationMinWidthDp = 600f

private enum class AppDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Dashboard(
        route = DashboardRoute,
        labelRes = R.string.navigation_dashboard,
        icon = Icons.Rounded.DonutLarge
    ),
    Settings(
        route = SettingsRoute,
        labelRes = R.string.settings_title,
        icon = Icons.Rounded.Settings
    )
}

@Composable
fun CodexBarApp(
    initialDestination: String,
    onScreenPrivacyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: initialDestination
    var initialDestinationHandled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialDestination, initialDestinationHandled) {
        if (!initialDestinationHandled) {
            initialDestinationHandled = true
            if (initialDestination == SettingsRoute) {
                navController.navigate(SettingsRoute) {
                    launchSingleTop = true
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (useExpandedNavigation(maxWidth.value)) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(88.dp),
                    header = {
                        Surface(
                            modifier = Modifier.padding(vertical = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                modifier = Modifier.padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DonutLarge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                ) {
                    AppDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigateTopLevel(destination.route)
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes)
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
                VerticalDivider()
                AppNavHost(
                    navController = navController,
                    onScreenPrivacyChanged = onScreenPrivacyChanged,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    navController.navigateTopLevel(destination.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.labelRes)
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                AppNavHost(
                    navController = navController,
                    onScreenPrivacyChanged = onScreenPrivacyChanged,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    onScreenPrivacyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier
    ) {
        composable(DashboardRoute) {
            DashboardScreen(
                onNavigateToSettings = {
                    navController.navigateTopLevel(SettingsRoute)
                }
            )
        }
        composable(SettingsRoute) {
            SettingsScreen(
                onNavigateBack = {
                    navController.navigateTopLevel(DashboardRoute)
                },
                showBackButton = false,
                onScreenPrivacyChanged = onScreenPrivacyChanged
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
