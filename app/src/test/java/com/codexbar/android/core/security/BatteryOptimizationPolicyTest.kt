package com.codexbar.android.core.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class BatteryOptimizationPolicyTest {
    private val repoDir: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun `app does not request unrestricted battery optimization exemption`() {
        val manifest = File(repoDir, "app/src/main/AndroidManifest.xml").readText()
        val mainActivity = File(
            repoDir,
            "app/src/main/java/com/codexbar/android/MainActivity.kt"
        ).readText()

        assertFalse(manifest.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertFalse(mainActivity.contains("ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertFalse(mainActivity.contains("BatteryOptimizationHelper"))
    }
}
