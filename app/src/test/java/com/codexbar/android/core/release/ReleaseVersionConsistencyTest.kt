package com.codexbar.android.core.release

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionConsistencyTest {
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun `Android companion documentation and release notes use one version`() {
        val androidBuild = File(repoDir, "app/build.gradle.kts").readText()
        val companionPackage = File(repoDir, "companion/gemini/package.json").readText()
        val readme = File(repoDir, "README.md").readText()
        val version = requireNotNull(
            Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(androidBuild)?.groupValues?.get(1)
        )
        val companionVersion = requireNotNull(
            Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(companionPackage)?.groupValues?.get(1)
        )

        assertEquals(version, companionVersion)
        assertTrue(version.matches(Regex("\\d+\\.\\d+\\.\\d+")))
        assertTrue(File(repoDir, "docs/releases/v$version.md").isFile)
        assertTrue(readme.contains("CodexBar-Gemini-Companion-v$version.zip"))
    }
}
