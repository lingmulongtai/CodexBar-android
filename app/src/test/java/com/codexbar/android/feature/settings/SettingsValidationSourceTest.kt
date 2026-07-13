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
        ).readText().replace("\r\n", "\n")

        val validationCall = source.indexOf("val result = repo.validateCredential(credential)")
        val successBranch = source.indexOf("is Result.Success -> {", startIndex = validationCall)
        val saveCredential = source.indexOf("prefsManager.saveCredential(service, credential)", startIndex = successBranch)
        val failureBranch = source.indexOf("is Result.Failure ->", startIndex = successBranch)

        assertTrue("manual validation must validate the candidate credential", validationCall >= 0)
        assertTrue("manual validation must save only from the success branch", successBranch > validationCall)
        assertTrue("manual validation must persist the credential after success", saveCredential > successBranch)
        assertTrue("manual validation must not save from the failure branch", failureBranch == -1 || saveCredential < failureBranch)
        assertFalse(source.contains("val result = repo.validateCredential()\n"))
    }
}
