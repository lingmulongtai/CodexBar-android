package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `manual credential validation does not overwrite active credential before success`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsViewModel.kt"
        ).readText()

        assertTrue(source.contains("val result = repo.validateCredential(credential)"))
        assertTrue(source.contains("is Result.Success -> {\n                    prefsManager.saveCredential(service, credential)"))
        assertFalse(source.contains("prefsManager.saveCredential(service, credential)\n\n            val result = repo.validateCredential()"))
    }
}
