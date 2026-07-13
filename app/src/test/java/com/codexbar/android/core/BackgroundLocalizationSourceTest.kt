package com.codexbar.android.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLocalizationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `notifications worker and tile use app locale resources`() {
        val notification = sourceFile("notification/QuotaNotificationService.kt")
        val worker = sourceFile("workmanager/QuotaRefreshWorker.kt")
        val tile = sourceFile("tile/QuotaTileService.kt")

        assertTrue(notification.contains("ContextCompat.getContextForLanguage(context)"))
        assertTrue(worker.contains("ContextCompat.getContextForLanguage(applicationContext)"))
        assertTrue(tile.contains("ContextCompat.getContextForLanguage(this)"))
        assertFalse(notification.contains("AI quota status"))
        assertFalse(notification.contains("Quota details hidden"))
        assertFalse(worker.contains("Refreshing quota data..."))
        assertFalse(tile.contains("Tap to set up"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/core/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
