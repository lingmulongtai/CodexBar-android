package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMonitoringSettingsSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `disabling notifications stops monitoring and clears posted notifications`() {
        val source = sourceFile("SettingsViewModel.kt")
        val toggleIndex = source.indexOf("fun setNotificationsEnabled(enabled: Boolean)")
        assertTrue("notification toggle must exist", toggleIndex >= 0)
        val toggle = source.substring(toggleIndex)

        assertTrue(toggle.contains("WorkManagerInitializer.stopMonitoringSession(appContext)"))
        assertTrue(toggle.contains("notificationService.cancelAllNotifications()"))
    }

    @Test
    fun `settings owns explicit live monitoring start and stop`() {
        val source = sourceFile("SettingsViewModel.kt")

        assertTrue(source.contains("fun startMonitoring()"))
        assertTrue(source.contains("WorkManagerInitializer.startMonitoringSession("))
        assertTrue(source.contains("notificationService.showMonitoringPlaceholder(session)"))
        assertTrue(source.contains("fun stopMonitoring()"))
        assertTrue(source.contains("notificationService.cancelMonitoringNotification()"))
    }

    private fun sourceFile(name: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/$name"
        ).readText().replace("\r\n", "\n")
    }
}
