package com.codexbar.android.core.network.codex.telemetry

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexTelemetryDeepLinkSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `pairing deep link is scoped cleared and never auto-connects`() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()
        val activity = sourceFile("MainActivity.kt")
        val screen = sourceFile("feature/settings/SettingsScreen.kt")

        assertTrue(manifest.contains("<data android:host=\"codex-telemetry-pair\" />"))
        assertTrue(activity.contains("codexTelemetryPairingUriOrNull(launchUri)"))
        assertTrue(activity.contains("intent?.data = null"))
        assertTrue(screen.contains("importCodexTelemetryPairingCode(initialCodexTelemetryPairingUri)"))
        assertTrue(screen.contains("onClick = onConnect"))
        assertFalse(
            screen.contains(
                "connectCodexTelemetryCompanion()\n            onCodexTelemetryPairingConsumed"
            )
        )
    }

    private fun sourceFile(relativePath: String): String {
        return File(appDir, "src/main/java/com/codexbar/android/$relativePath")
            .readText()
            .replace("\r\n", "\n")
    }
}
