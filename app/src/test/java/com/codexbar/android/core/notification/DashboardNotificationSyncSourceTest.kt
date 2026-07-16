package com.codexbar.android.core.notification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardNotificationSyncSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `dashboard refresh immediately publishes its presentation snapshot`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/dashboard/DashboardViewModel.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("_uiState.value = DashboardUiState.Content(snapshot)"))
        assertTrue(source.contains("notificationService.publishSnapshot("))
        assertTrue(source.contains("monitoringSession = monitoringSessionStore.activeSession()"))
    }

    @Test
    fun `background refresh publishes errors and successful data through the same path`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/core/workmanager/QuotaRefreshWorker.kt"
        ).readText().replace("\r\n", "\n")

        val mapIndex = source.indexOf("val snapshot = presentationMapper.map(")
        val publishIndex = source.indexOf("notificationService.publishSnapshot(")
        assertTrue(mapIndex >= 0)
        assertTrue(publishIndex > mapIndex)
    }
}
