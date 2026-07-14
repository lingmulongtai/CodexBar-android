package com.codexbar.android.core.release

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowActionPinningTest {
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun `external workflow actions are pinned to immutable commits`() {
        val usesLines = listOf("android.yml", "release.yml")
            .flatMap { name ->
                File(repoDir, ".github/workflows/$name")
                    .readLines()
                    .map(String::trim)
                    .filter { it.startsWith("uses:") || it.startsWith("- uses:") }
                    .map { it.removePrefix("- ") }
            }

        assertTrue(usesLines.isNotEmpty())
        usesLines.forEach { line ->
            assertTrue(
                "Workflow action must use a full commit SHA: $line",
                IMMUTABLE_ACTION_REF.matches(line)
            )
        }
    }

    private companion object {
        val IMMUTABLE_ACTION_REF = Regex(
            "^uses:\\s+[^@\\s]+@[0-9a-f]{40}(?:\\s+#\\s+.+)?$"
        )
    }
}
