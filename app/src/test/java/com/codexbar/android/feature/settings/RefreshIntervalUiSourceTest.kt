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

        assertTrue(source.contains("R.string.background_refresh_title"))
        assertTrue(source.contains("R.string.background_refresh_manual_only"))
        assertTrue(source.contains("RefreshIntervalPolicy.MIN_MINUTES"))
        assertTrue(source.contains("RefreshIntervalPolicy.MAX_MINUTES"))
        assertTrue(source.contains("Slider("))
    }
}
