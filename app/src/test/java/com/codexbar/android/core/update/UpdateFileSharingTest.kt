package com.codexbar.android.core.update

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class UpdateFileSharingTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
    private fun xml(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(app, "src/main/$path"))

    @Test fun `installer only receives access to the private updates directory`() {
        val providers = xml("AndroidManifest.xml").getElementsByTagName("provider")
        val provider = (0 until providers.length).map { providers.item(it) as Element }
            .single { it.getAttribute("android:name") == ".core.update.UpdateFileProvider" }
        assertEquals("false", provider.getAttribute("android:exported"))
        assertEquals("true", provider.getAttribute("android:grantUriPermissions"))
        val paths = xml("res/xml/update_file_paths.xml").documentElement.childNodes
        val roots = (0 until paths.length).mapNotNull { paths.item(it) as? Element }
        assertEquals(1, roots.size)
        assertEquals("files-path", roots.single().tagName)
        assertEquals("updates/", roots.single().getAttribute("path"))
    }

    @Test fun `APK downloads are excluded from cloud backup and device transfer`() {
        for (path in listOf("res/xml/backup_rules.xml", "res/xml/data_extraction_rules.xml")) {
            val entries = xml(path).getElementsByTagName("exclude")
            val updates = (0 until entries.length).map { entries.item(it) as Element }
                .filter { it.getAttribute("domain") == "file" && it.getAttribute("path") == "updates/" }
            assertEquals(if (path.contains("data_extraction")) 2 else 1, updates.size)
        }
    }
}
