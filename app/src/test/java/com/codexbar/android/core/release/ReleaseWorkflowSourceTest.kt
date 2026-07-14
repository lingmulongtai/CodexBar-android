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

    @Test
    fun `release signing material is scoped to the build step and deleted`() {
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()
        val buildStep = workflow
            .substringAfter("- name: Build signed release artifacts")
            .substringBefore("- name: Verify release signing identity")

        assertTrue(buildStep.contains("ANDROID_KEYSTORE_BASE64: \${{ secrets.ANDROID_KEYSTORE_BASE64 }}"))
        assertTrue(buildStep.contains("trap cleanup EXIT"))
        assertTrue(buildStep.contains("rm -f \"\$keystore_path\""))
        assertTrue(buildStep.contains("chmod 600 \"\$keystore_path\""))
        assertFalse(workflow.contains("GITHUB_ENV"))
    }

    @Test
    fun `signed release is cold started before publication`() {
        val workflow = File(repoDir, ".github/workflows/release.yml").readText()
        val smokeStep = workflow
            .substringAfter("- name: Smoke test signed release APK")
            .substringBefore("- name: Prepare release bundle")

        assertTrue(smokeStep.contains("ReactiveCircus/android-emulator-runner@"))
        assertTrue(smokeStep.contains("api-level: 36"))
        assertTrue(smokeStep.contains("set -eu"))
        assertFalse(smokeStep.contains("pipefail"))
        assertFalse(smokeStep.contains("mapfile"))
        assertFalse(smokeStep.contains("< <("))
        assertTrue(smokeStep.contains("adb install"))
        assertTrue(smokeStep.contains("adb shell am start -W"))
        assertTrue(smokeStep.contains("adb shell pidof"))
        assertTrue(smokeStep.contains("adb logcat -b crash -d"))
        assertTrue(
            workflow.indexOf("- name: Smoke test signed release APK") <
                workflow.indexOf("- name: Publish GitHub Release")
        )
    }
}
