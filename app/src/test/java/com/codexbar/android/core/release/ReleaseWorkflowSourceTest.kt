package com.codexbar.android.core.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseWorkflowSourceTest {
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun `tagged releases require versioned notes and publish as stable latest`() {
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()

        assertTrue(workflow.contains("docs/releases/"))
        assertTrue(workflow.contains("GITHUB_REF_NAME"))
        assertTrue(workflow.contains("test -f \"\$notes_file\""))
        assertTrue(workflow.contains("--notes-file"))
        assertTrue(workflow.contains("--verify-tag"))
        assertTrue(workflow.contains("--latest"))
        assertFalse(workflow.contains("--prerelease"))
    }
}
