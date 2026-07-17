package com.codexbar.android.core.network.gemini

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPairingDeepLinkSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `pairing deep link is narrowly scoped and clears the secret from the activity intent`() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()
        val activity = File(
            appDir,
            "src/main/java/com/codexbar/android/MainActivity.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(manifest.contains("<data android:scheme=\"codexbar\" />"))
        assertTrue(manifest.contains("<data android:host=\"gemini-pair\" />"))
        assertTrue(activity.contains("geminiPairingUriOrNull(launchUri)"))
        assertTrue(activity.contains("intent?.data = null"))
        assertTrue(activity.contains("intent.data = null"))
        assertFalse(activity.contains("Log."))
    }

    @Test
    fun `a scanned pairing fills connections but requires an explicit verification tap`() {
        val screen = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(screen.contains("viewModel.importGeminiPairingCode(initialGeminiPairingUri)"))
        assertTrue(screen.contains("onClick = onConnect"))
        assertFalse(screen.contains("viewModel.connectGeminiCompanion()\n            onGeminiPairingConsumed"))
    }
}
