package com.codexbar.android.core.notification

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNotificationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `api 36 monitoring notification requests promoted ongoing treatment`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/core/notification/QuotaNotificationService.kt"
        ).readText().replace("\r\n", "\n")
        val builderIndex = source.indexOf("private fun buildPlatformMonitoringNotification(")
        assertTrue("platform monitoring builder must exist", builderIndex >= 0)
        val builder = source.substring(builderIndex)

        assertTrue(builder.contains("putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)"))
        assertTrue(builder.contains(".setChronometerCountDown(true)"))
        assertTrue(builder.contains(".setShortCriticalText(criticalText)"))
        assertFalse(builder.contains("setFlag(Notification.FLAG_PROMOTED_ONGOING"))
    }
}
