package com.codexbar.android.core.notification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetNotificationPrivacySourceTest {
    private val appDir = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test fun `privacy refresh removes previously public reset alerts before any early return`() {
        val source = File(appDir, "src/main/java/com/codexbar/android/core/notification/QuotaNotificationService.kt")
            .readText().replace("\r\n", "\n")
        val refresh = source.substringAfter("fun refreshPrivacySettings(session: MonitoringSession?) {")
            .substringBefore("fun cancelResetNotifications")
        assertTrue(refresh.indexOf("cancelResetNotifications()") >= 0)
        assertTrue(refresh.indexOf("cancelResetNotifications()") < refresh.indexOf("return"))
        assertTrue(source.contains("entry.notification.channelId == RESET_CHANNEL_ID"))
    }
}
