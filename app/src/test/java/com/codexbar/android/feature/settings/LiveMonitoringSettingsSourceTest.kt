package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMonitoringSettingsSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `persistent notification toggle does not stop an active live monitor`() {
        val source = sourceFile("SettingsViewModel.kt")
        val toggleIndex = source.indexOf("fun setPersistentNotificationEnabled(enabled: Boolean)")
        assertTrue("notification toggle must exist", toggleIndex >= 0)
        val nextFunctionIndex = source.indexOf("\n    fun ", toggleIndex + 8)
        val toggle = source.substring(toggleIndex, nextFunctionIndex)

        assertTrue(toggle.contains("notificationService.cancelQuotaNotification()"))
        assertFalse(toggle.contains("stopMonitoringSession"))
        assertFalse(toggle.contains("cancelAllNotifications"))
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
