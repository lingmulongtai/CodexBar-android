package com.codexbar.android.core.presentation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaPresentationLocalizationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `runtime presentation paths use app locale text`() {
        val mapper = sourceFile("core/presentation/QuotaPresentationMapper.kt")
        val pace = sourceFile("core/presentation/QuotaPaceCalculator.kt")
        val dashboard = sourceFile("feature/dashboard/DashboardViewModel.kt")
        val worker = sourceFile("core/workmanager/QuotaRefreshWorker.kt")
        val history = sourceFile("core/data/QuotaHistoryStore.kt")

        assertFalse(mapper.contains("No fresh data"))
        assertFalse(pace.contains("Pace on track"))
        assertTrue(dashboard.contains("AndroidQuotaPresentationText(appContext)"))
        assertTrue(worker.contains("AndroidQuotaPresentationText(context)"))
        assertTrue(history.contains("QuotaPaceCalculator(AndroidQuotaPresentationText(context))"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
