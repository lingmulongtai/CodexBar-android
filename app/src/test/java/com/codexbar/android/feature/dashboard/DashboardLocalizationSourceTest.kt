package com.codexbar.android.feature.dashboard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLocalizationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `dashboard surfaces resolve user text from resources`() {
        val files = listOf(
            "DashboardScreen.kt",
            "ServiceCard.kt",
            "ServiceDetailSheet.kt",
            "QuotaGaugeBar.kt"
        ).map { name ->
            File(
                appDir,
                "src/main/java/com/codexbar/android/feature/dashboard/$name"
            ).readText().replace("\r\n", "\n")
        }

        assertTrue(files.all { it.contains("stringResource") })
        assertFalse(files.any { it.contains("No services configured") })
        assertFalse(files.any { it.contains("Re-authentication required") })
        assertFalse(files.any { it.contains("Quota windows") })
    }
}
