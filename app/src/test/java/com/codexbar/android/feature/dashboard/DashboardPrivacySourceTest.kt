package com.codexbar.android.feature.dashboard

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPrivacySourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `widget redaction does not hide dashboard quota values`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/dashboard/DashboardViewModel.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("redactSensitiveValues = false"))
        assertTrue(source.contains("widgetRedacted = privacySettings.widgetRedactionEnabled"))
    }
}
