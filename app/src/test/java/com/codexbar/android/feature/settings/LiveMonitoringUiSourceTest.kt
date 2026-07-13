package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMonitoringUiSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `dashboard no longer exposes unlabeled monitoring icons`() {
        val screen = sourceFile("feature/dashboard/DashboardScreen.kt")
        val viewModel = sourceFile("feature/dashboard/DashboardViewModel.kt")

        assertFalse(screen.contains("Icons.Default.PlayArrow"))
        assertFalse(screen.contains("Icons.Default.Stop"))
        assertFalse(screen.contains("viewModel.startMonitoring()"))
        assertFalse(screen.contains("viewModel.stopMonitoring()"))
        assertFalse(viewModel.contains("fun startMonitoring()"))
        assertFalse(viewModel.contains("fun stopMonitoring()"))
    }

    @Test
    fun `settings provides labeled live monitor controls and system access`() {
        val source = sourceFile("feature/settings/SettingsScreen.kt")

        assertTrue(source.contains("Notifications & live monitor"))
        assertTrue(source.contains("Start live monitor"))
        assertTrue(source.contains("Stop live monitor"))
        assertTrue(source.contains("ActivityResultContracts.RequestPermission()"))
        assertTrue(source.contains("Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS"))
    }

    @Test
    fun `app launch does not request notification permission without context`() {
        val source = sourceFile("MainActivity.kt")

        assertFalse(source.contains("rememberPermissionState"))
        assertFalse(source.contains("launchPermissionRequest"))
        assertFalse(source.contains("Manifest.permission.POST_NOTIFICATIONS"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
