package com.codexbar.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codexbar.android.core.util.BatteryOptimizationHelper
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.settings.SettingsScreen
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefsManager: EncryptedPrefsManager

    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from the battery optimization dialog,
        // schedule workers regardless of the result
        WorkManagerInitializer.applySavedRefreshPolicyAsync(this)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDestination = startDestinationForHost(intent?.data?.host)
        applyScreenPrivacy(prefsManager.getPrivacySettings().screenPrivacyEnabled)
        lifecycleScope.launch {
            prefsManager.warmCache()
            applyScreenPrivacy(prefsManager.getPrivacySettings().screenPrivacyEnabled)
            WorkManagerInitializer.applyRefreshPolicy(
                this@MainActivity,
                prefsManager.getRefreshInterval()
            )
        }
        enableEdgeToEdge()

        setContent {
            CodexBarTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                // Android 13+ notification permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

                    LaunchedEffect(permissionState.status.isGranted) {
                        if (!permissionState.status.isGranted) {
                            permissionState.launchPermissionRequest()
                        }
                    }

                    LaunchedEffect(permissionState.status) {
                        if (!permissionState.status.isGranted) {
                            snackbarHostState.showSnackbar(
                                "Notification permission required for background quota updates"
                            )
                        }
                    }
                }

                // Battery optimization exemption
                var showBatteryDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)) {
                        showBatteryDialog = true
                    }
                }

                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showBatteryDialog = false
                        },
                        title = { Text("Background Token Refresh") },
                        text = {
                            Text(
                                "To keep your API tokens up to date in the background, " +
                                    "please exempt this app from battery optimization."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                batteryOptLauncher.launch(
                                    BatteryOptimizationHelper
                                        .requestIgnoreBatteryOptimizationsIntent(this@MainActivity)
                                )
                            }) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                // Fallback: open system battery settings
                                try {
                                    batteryOptLauncher.launch(
                                        BatteryOptimizationHelper
                                            .openBatteryOptimizationSettingsIntent()
                                    )
                                } catch (_: Exception) {
                                    // Ignore if settings page is not available
                                }
                            }) {
                                Text("Settings")
                            }
                        }
                    )
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = initialDestination,
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    if (!navController.popBackStack()) {
                                        finish()
                                    }
                                },
                                onScreenPrivacyChanged = { enabled ->
                                    applyScreenPrivacy(enabled)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyScreenPrivacy(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

internal fun startDestinationForHost(host: String?): String {
    return if (host == "settings") "settings" else "dashboard"
}
