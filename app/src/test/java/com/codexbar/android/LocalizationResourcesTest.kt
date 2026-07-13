package com.codexbar.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `japanese resources cover every default string`() {
        val defaultStrings = stringNames(File(appDir, "src/main/res/values/strings.xml"))
        val japaneseStrings = stringNames(File(appDir, "src/main/res/values-ja/strings.xml"))

        assertTrue("default resources must not be empty", defaultStrings.isNotEmpty())
        assertEquals(defaultStrings, japaneseStrings)
    }

    private fun stringNames(file: File): Set<String> {
        val namePattern = Regex("""<string name="([^"]+)"""")
        return namePattern.findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }
}
