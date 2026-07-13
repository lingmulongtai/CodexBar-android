package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshIntervalUiSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `settings exposes a bounded refresh slider and manual mode`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("Background refresh"))
        assertTrue(source.contains("Manual only"))
        assertTrue(source.contains("RefreshIntervalPolicy.MIN_MINUTES"))
        assertTrue(source.contains("RefreshIntervalPolicy.MAX_MINUTES"))
        assertTrue(source.contains("Slider("))
    }
}
