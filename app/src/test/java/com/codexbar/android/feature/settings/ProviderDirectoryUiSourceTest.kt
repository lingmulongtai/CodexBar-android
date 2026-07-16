package com.codexbar.android.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDirectoryUiSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `provider directory is searchable filterable and single-expand`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("ProviderDirectoryControls("))
        assertTrue(source.contains("R.string.provider_search_label"))
        assertTrue(source.contains("ProviderConnectionFilter.entries.forEachIndexed"))
        assertTrue(source.contains("ProviderCategoryFilter.entries.forEach"))
        assertTrue(source.contains("var expandedProviderName by rememberSaveable"))
        assertTrue(source.contains("expanded = expandedProviderName == service.name"))
        assertTrue(source.contains("if (expanded) {"))
    }

    @Test
    fun `provider rows expose category auth and connection state while collapsed`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/settings/SettingsScreen.kt"
        ).readText().replace("\r\n", "\n")

        assertTrue(source.contains("service.providerMetadata.category.labelRes()"))
        assertTrue(source.contains("service.providerMetadata.authMode.labelRes()"))
        assertTrue(source.contains("R.string.status_connected"))
        assertTrue(source.contains("R.string.status_not_connected"))
    }
}
