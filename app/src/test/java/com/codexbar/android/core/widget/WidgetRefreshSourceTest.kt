package com.codexbar.android.core.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `widget configuration requests fresh data after saving`() {
        val source = sourceFile("WidgetConfigurationActivity.kt")
        val functionIndex = source.indexOf("private fun confirmSelection(")
        assertTrue("confirmSelection must exist", functionIndex >= 0)
        val confirmSelection = source.substring(functionIndex)

        assertTrue(
            confirmSelection.contains("WorkManagerInitializer.enqueueManualQuotaRefresh(") &&
                confirmSelection.contains("source = \"widget_config\"")
        )
    }

    @Test
    fun `launcher widget updates request fresh data`() {
        val source = sourceFile("QuotaWidgetReceiver.kt")
        val functionIndex = source.indexOf("override fun onUpdate(")
        assertTrue("onUpdate must exist", functionIndex >= 0)
        val onUpdate = source.substring(functionIndex)

        assertTrue(
            onUpdate.contains("WorkManagerInitializer.enqueueManualQuotaRefresh(") &&
                onUpdate.contains("source = \"widget_update\"")
        )
    }

    @Test
    fun `widget receiver is not exposed to untrusted application broadcasts`() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml")
            .readText()
            .replace("\r\n", "\n")
        val receiver = manifest
            .substringAfter("android:name=\".core.widget.QuotaWidgetReceiver\"")
            .substringBefore("</receiver>")

        assertTrue(receiver.contains("android:exported=\"false\""))
    }

    @Test
    fun `explicit widget refresh supersedes stale manual work`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/core/workmanager/WorkManagerInitializer.kt"
        ).readText().replace("\r\n", "\n")
        val start = source.indexOf("fun enqueueManualQuotaRefresh(")
        val end = source.indexOf("fun startMonitoringSession(", start)
        assertTrue("enqueueManualQuotaRefresh must exist", start >= 0 && end > start)
        val enqueueManualRefresh = source.substring(start, end)

        assertTrue(enqueueManualRefresh.contains("ExistingWorkPolicy.REPLACE"))
        assertFalse(enqueueManualRefresh.contains("ExistingWorkPolicy.KEEP"))
    }

    @Test
    fun `provider failures replace waiting state with an actionable message`() {
        val worker = sourceFileFromCore("workmanager/QuotaRefreshWorker.kt")
        val widgetPrefs = sourceFile("WidgetPrefsManager.kt")
        val widget = sourceFile("QuotaGlanceWidget.kt")

        assertTrue(worker.contains("errors[service] = result.error"))
        assertTrue(worker.contains("errors = errors"))
        assertTrue(worker.contains("cacheQuotaData(snapshot)"))
        assertTrue(widgetPrefs.contains("service.freshness.staleReason?.let"))
        assertTrue(widgetPrefs.contains("fun getCachedStatusMessage("))
        assertTrue(widget.contains("statusMessage ?: strings.waitingForData"))
    }

    private fun sourceFile(name: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/core/widget/$name"
        ).readText().replace("\r\n", "\n")
    }

    private fun sourceFileFromCore(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/core/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
