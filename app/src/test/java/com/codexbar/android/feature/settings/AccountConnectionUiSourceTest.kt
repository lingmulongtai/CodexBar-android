package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountConnectionUiSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `device code is selectable copyable and opens the sign-in page`() {
        val source = settingsSource()

        assertTrue(source.contains("SelectionContainer(modifier = Modifier.weight(1f))"))
        assertTrue(source.contains("deviceCodeForClipboard(prompt.userCode)"))
        assertTrue(source.contains("onCopyAccountCode(clipboardCode)"))
        assertTrue(source.contains("copiedCode = clipboardCode"))
        assertTrue(source.contains("onOpenAccountLink(prompt.verificationUrl)"))
        assertFalse(source.contains("lastOpenedCode"))
        assertFalse(source.contains("LaunchedEffect(prompt?.userCode)"))

        val promptCard = source.indexOf("prompt?.let")
        val copyAction = source.indexOf("onCopyAccountCode(clipboardCode)", promptCard)
        val openAction = source.indexOf("onOpenAccountLink(prompt.verificationUrl)", copyAction)
        assertTrue("copy action must be presented before browser navigation", copyAction in 0..<openAction)
    }

    @Test
    fun `safe account flow precedes an explicit manual fallback`() {
        val source = settingsSource()
        val accountLink = source.indexOf("AccountLinkControls(")
        val manualFields = source.indexOf("ManualCredentialFields(")

        assertTrue(accountLink >= 0)
        assertTrue(manualFields > accountLink)
        assertTrue(source.contains("R.string.account_manual_setup"))
        assertTrue(source.contains("R.string.account_security_note"))
    }

    private fun settingsSource(): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")
    }
}
