package com.codexbar.android.core.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataSynchronizationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `dashboard refresh writes and renders the same widget snapshot`() {
        val dashboard = sourceFile("feature/dashboard/DashboardViewModel.kt")
        val publishStart = dashboard.indexOf("private suspend fun publishSnapshot(")
        assertTrue("publishSnapshot must exist", publishStart >= 0)
        val publish = dashboard.substring(publishStart)

        assertTrue(publish.contains("snapshot.services.forEach(widgetPrefsManager::cachePresentation)"))
        assertTrue(publish.contains("QuotaGlanceWidget().updateAll(appContext)"))
    }

    @Test
    fun `worker renders widgets even when no account remains`() {
        val worker = sourceFile("core/workmanager/QuotaRefreshWorker.kt")
        val emptyRepos = worker.substringAfter("if (repos.isEmpty()) {")
            .substringBefore("return Result.success()")

        assertTrue(emptyRepos.contains("QuotaGlanceWidget().updateAll(applicationContext)"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
