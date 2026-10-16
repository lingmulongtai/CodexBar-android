package com.codexbar.android.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Default-on reset alerts must request Android's separate runtime permission once a source exists. */
@Composable
internal fun ResetNotificationPermissionSetup(enabled: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(enabled) {
        if (!enabled || Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        val prefs = context.getSharedPreferences("notification_permission_setup", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reset_prompt_attempted", false)) {
            prefs.edit().putBoolean("reset_prompt_attempted", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
