package com.codexbar.android.core.workmanager

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseRoomKeepRulesTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "proguard-rules.pro").isFile }

    @Test
    fun `release keeps constructors used by Room reflection`() {
        val rules = File(appDir, "proguard-rules.pro")
            .readText()
            .replace("\r\n", "\n")

        val roomRule = Regex(
            """-keep class \* extends androidx\.room\.RoomDatabase\s*\{([^}]*)}"""
        ).find(rules)

        assertTrue("Expected a keep rule for Room database implementations", roomRule != null)
        assertTrue(
            "Expected the Room keep rule to preserve a public no-argument constructor",
            roomRule?.groupValues?.get(1)?.contains("public <init>();") == true
        )
    }
}
