package com.codexbar.android.feature.dashboard

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardContrastSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `custom dashboard containers define readable content colors`() {
        val sourceDir = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/dashboard"
        )
        val serviceCard = File(sourceDir, "ServiceCard.kt").readText()
        val telemetryCard = File(sourceDir, "CodexTelemetryCards.kt").readText()

        assertTrue(
            serviceCard.contains("contentColor = MaterialTheme.colorScheme.onSurface")
        )
        assertTrue(
            telemetryCard.contains("contentColor = MaterialTheme.colorScheme.onSurface")
        )
    }
}
