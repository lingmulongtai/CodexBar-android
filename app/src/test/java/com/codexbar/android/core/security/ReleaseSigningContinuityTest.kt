package com.codexbar.android.core.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningContinuityTest {
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }
    private val appDir = File(repoDir, "app")

    @Test
    fun `release workflow pins the established signing certificate`() {
        val fingerprintFile = File(repoDir, ".github/release-signing-cert-sha256.txt")
        val fingerprint = fingerprintFile.readText().trim()
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()

        assertEquals(EXPECTED_RELEASE_CERTIFICATE_SHA256, fingerprint)
        assertTrue(workflow.contains("Verify release signing identity"))
        assertTrue(workflow.contains("apksigner\" verify --verbose --print-certs"))
        assertTrue(workflow.contains("Number of signers:"))
        assertTrue(workflow.contains("release-signing-cert-sha256.txt"))
    }

    @Test
    fun `upgrade identity and secure storage names remain stable`() {
        val appBuild = File(appDir, "build.gradle.kts").readText()
        val securePrefs = File(
            appDir,
            "src/main/java/com/codexbar/android/core/security/EncryptedPrefsManager.kt"
        ).readText()

        assertTrue(appBuild.contains("applicationId = \"com.codexbar.android\""))
        assertTrue(securePrefs.contains("SECURE_DATASTORE_NAME = \"codexbar_secure_prefs\""))
        assertTrue(securePrefs.contains("KEY_ALIAS = \"codexbar_secure_prefs_aes_gcm\""))
    }

    private companion object {
        const val EXPECTED_RELEASE_CERTIFICATE_SHA256 =
            "3ea66ed2f88daf035f3feb470d1dcb397f8e33f1593d302f662eb20ab5ec608f"
    }
}
