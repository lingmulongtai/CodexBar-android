package com.codexbar.android.core.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePreferencesImplementationTest {

    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun `secure preferences use datastore and value encryption`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/core/security/EncryptedPrefsManager.kt"
        ).readText()

        assertTrue(source.contains("preferencesDataStore"))
        assertTrue(source.contains("AndroidKeyStore"))
        assertTrue(source.contains("AES/GCM/NoPadding"))
        assertFalse(source.contains("EncryptedSharedPreferences"))
        assertFalse(source.contains("MasterKeys"))
    }

    @Test
    fun `security crypto dependency is removed`() {
        val appBuild = File(appDir, "build.gradle.kts").readText()
        val versionCatalog = File(repoDir, "gradle/libs.versions.toml").readText()

        assertFalse(appBuild.contains("security.crypto"))
        assertFalse(versionCatalog.contains("security-crypto"))
    }
}
