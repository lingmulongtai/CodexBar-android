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

    @Test
    fun `monitoring starts with a placeholder and a system countdown`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/core/notification/QuotaNotificationService.kt"
        ).readText().replace("\r\n", "\n")
        val monitoringIndex = source.indexOf("fun showMonitoringNotification(")
        val platformBuilderIndex = source.indexOf("private fun buildPlatformMonitoringNotification(")
        assertTrue("monitoring builder must exist", monitoringIndex >= 0)
        assertTrue("platform builder must exist", platformBuilderIndex > monitoringIndex)
        val compatBuilder = source.substring(monitoringIndex, platformBuilderIndex)

        assertTrue(compatBuilder.contains("R.string.notification_monitoring_waiting"))
        assertTrue(compatBuilder.contains(".setWhen(session.endsAtMillis)"))
        assertTrue(compatBuilder.contains(".setUsesChronometer(true)"))
        assertTrue(compatBuilder.contains(".setChronometerCountDown(true)"))
        assertTrue(source.contains("fun showMonitoringPlaceholder(session: MonitoringSession)"))
        assertTrue(source.contains("services = emptyList()"))
    }
}
