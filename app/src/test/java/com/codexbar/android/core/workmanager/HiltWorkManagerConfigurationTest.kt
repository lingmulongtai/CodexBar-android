package com.codexbar.android.core.workmanager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiltWorkManagerConfigurationTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `application supplies the Hilt worker factory`() {
        val application = sourceFile("com/codexbar/android/CodexBarApplication.kt")

        assertTrue(application.contains("Application(), Configuration.Provider"))
        assertTrue(application.contains("lateinit var workerFactory: HiltWorkerFactory"))
        assertTrue(application.contains(".setWorkerFactory(workerFactory)"))
    }

    @Test
    fun `default WorkManager initializer is removed`() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml")
            .readText()
            .replace("\r\n", "\n")
        val initializer = sourceFile(
            "com/codexbar/android/core/workmanager/WorkManagerInitializer.kt"
        )

        assertTrue(manifest.contains("android:name=\"androidx.work.WorkManagerInitializer\""))
        assertTrue(manifest.contains("tools:node=\"remove\""))
        assertTrue(initializer.contains("return emptyList()"))
        assertFalse(initializer.contains("AndroidWorkManagerInitializer"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(appDir, "src/main/java/$relativePath")
            .readText()
            .replace("\r\n", "\n")
    }
}
