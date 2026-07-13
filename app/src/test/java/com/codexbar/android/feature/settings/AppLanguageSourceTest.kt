package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `compose activities support appcompat locale changes`() {
        val mainActivity = sourceFile("MainActivity.kt")
        val widgetActivity = sourceFile("core/widget/WidgetConfigurationActivity.kt")

        assertTrue(mainActivity.contains("class MainActivity : AppCompatActivity()"))
        assertTrue(widgetActivity.contains("class WidgetConfigurationActivity : AppCompatActivity()"))
    }

    @Test
    fun `settings exposes system english and japanese choices`() {
        val screen = sourceFile("feature/settings/SettingsScreen.kt")
        val japaneseResources = File(appDir, "src/main/res/values-ja/strings.xml")

        assertTrue(screen.contains("LanguageSection("))
        assertTrue(screen.contains("language.apply()"))
        assertTrue(japaneseResources.isFile)
        assertTrue(japaneseResources.readText().contains("アプリの言語"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
