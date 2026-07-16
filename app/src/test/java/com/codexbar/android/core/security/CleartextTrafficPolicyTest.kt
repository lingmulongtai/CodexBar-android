package com.codexbar.android.core.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextTrafficPolicyTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `HTTP cleartext is disabled and provider URLs stay on HTTPS`() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()
        val kotlinSource = File(appDir, "src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(kotlinSource.contains("\"http://"))
    }
}
