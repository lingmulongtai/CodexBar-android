package com.codexbar.android.core.release

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionConsistencyTest {
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun `Android companions documentation screenshots and release notes use one version`() {
        val androidBuild = File(repoDir, "app/build.gradle.kts").readText()
        val readme = File(repoDir, "README.md").readText()
        val version = requireNotNull(
            Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(androidBuild)?.groupValues?.get(1)
        )
        val companionVersions = listOf("gemini", "codex").map { companion ->
            val companionPackage = File(
                repoDir,
                "companion/$companion/package.json"
            ).readText()
            requireNotNull(
                Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
                    .find(companionPackage)
                    ?.groupValues
                    ?.get(1)
            )
        }

        companionVersions.forEach { companionVersion ->
            assertEquals(version, companionVersion)
        }
        assertTrue(version.matches(Regex("\\d+\\.\\d+\\.\\d+")))
        assertTrue(File(repoDir, "docs/releases/v$version.md").isFile)
        assertTrue(readme.contains("CodexBar-Gemini-Companion-v$version.zip"))
        assertTrue(readme.contains("CodexBar-Codex-Telemetry-Companion-v$version.zip"))
        val screenshots = File(repoDir, "docs/images/releases/v$version")
            .listFiles { file -> file.extension == "png" }
            .orEmpty()
        assertTrue(screenshots.size >= 6)
    }
}
