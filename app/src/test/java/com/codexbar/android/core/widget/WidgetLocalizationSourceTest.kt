package com.codexbar.android.core.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLocalizationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `configuration and glance widget use localized resources`() {
        val configuration = sourceFile("WidgetConfigurationActivity.kt")
        val widget = sourceFile("QuotaGlanceWidget.kt")

        assertTrue(configuration.contains("stringResource(R.string.widget_setup_title)"))
        assertTrue(widget.contains("ContextCompat.getContextForLanguage(context)"))
        assertTrue(widget.contains("R.string.widget_waiting_for_data"))
        assertFalse(configuration.contains("Text(\""))
        assertFalse(widget.contains("Waiting for data..."))
        assertFalse(widget.contains("No services configured"))
    }

    private fun sourceFile(fileName: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/core/widget/$fileName"
        ).readText().replace("\r\n", "\n")
    }
}
