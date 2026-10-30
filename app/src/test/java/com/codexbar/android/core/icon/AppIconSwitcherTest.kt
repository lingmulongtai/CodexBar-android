package com.codexbar.android.core.icon

import com.codexbar.android.core.domain.model.AppThemeStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconSwitcherTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val manifest = File(appDir, "src/main/AndroidManifest.xml").readText().replace("\r\n", "\n")

    @Test
    fun `every design has its own launcher alias`() {
        val aliases = AppThemeStyle.entries.map(AppIconSwitcher::aliasFor)
        assertEquals(aliases.size, aliases.distinct().size)
        aliases.forEach { alias ->
            assertTrue(alias, manifest.contains("android:name=\".$alias\""))
        }
    }

    @Test
    fun `only the default design's alias launches a fresh install`() {
        val enabled = Regex("""<activity-alias\s+android:name="\.(\w+)"\s+android:enabled="true"""")
            .findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf(AppIconSwitcher.aliasFor(AppThemeStyle.MATERIAL_3)), enabled)
    }

    @Test
    fun `the main activity keeps deep links but leaves launching to the aliases`() {
        val activity = manifest.substringAfter("android:name=\".MainActivity\"").substringBefore("</activity>")
        assertFalse(activity.contains("android.intent.category.LAUNCHER"))
        assertTrue(activity.contains("<data android:scheme=\"codexbar\" />"))
        // The retired alias stays until an installed Instrument launcher migrates.
        assertTrue(manifest.contains("android:name=\".LauncherInstrument\""))
        assertEquals(AppThemeStyle.entries.size + 1, Regex("android.intent.category.LAUNCHER").findAll(manifest).count())
    }
}
