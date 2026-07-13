package com.codexbar.android.core.widget

import java.io.File
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

    private fun sourceFile(name: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/core/widget/$name"
        ).readText().replace("\r\n", "\n")
    }
}
