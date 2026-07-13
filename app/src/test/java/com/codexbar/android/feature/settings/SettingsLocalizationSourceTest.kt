package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLocalizationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `settings surfaces resolve user-facing copy from resources`() {
        val screen = sourceFile("SettingsScreen.kt")
        val viewModel = sourceFile("SettingsViewModel.kt")

        assertTrue(screen.contains("stringResource(R.string.notifications_title)"))
        assertTrue(screen.contains("stringResource(R.string.privacy_title)"))
        assertTrue(viewModel.contains("appContext.getString(R.string.validation_required_fields)"))
        assertFalse(screen.contains("Text(\""))
        assertFalse(viewModel.contains("Network error:"))
        assertFalse(viewModel.contains("Account link failed"))
    }

    private fun sourceFile(fileName: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/$fileName"
        ).readText().replace("\r\n", "\n")
    }
}
