package com.codexbar.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.codexbar.android.core.util.BatteryOptimizationHelper
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var prefsManager: EncryptedPrefsManager

    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from the battery optimization dialog,
        // schedule workers regardless of the result
        WorkManagerInitializer.applySavedRefreshPolicyAsync(this)
    }

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
                        title = { Text(stringResource(R.string.battery_refresh_title)) },
                        text = {
                            Text(stringResource(R.string.battery_refresh_message))
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                batteryOptLauncher.launch(
                                    BatteryOptimizationHelper
                                        .requestIgnoreBatteryOptimizationsIntent(this@MainActivity)
                                )
                            }) {
                                Text(stringResource(R.string.action_allow))
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
                                Text(stringResource(R.string.action_settings))
                            }
                        }
                    )
                }

                CodexBarApp(
                    initialDestination = initialDestination,
                    onScreenPrivacyChanged = ::applyScreenPrivacy
                )
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
