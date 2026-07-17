package com.codexbar.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionNavigationSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `connections is a dedicated top level destination`() {
        val app = sourceFile("CodexBarApp.kt")
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()

        assertTrue(app.contains("private const val ConnectionsRoute = \"connections\""))
        assertTrue(app.contains("Connections(\n        route = ConnectionsRoute"))
        assertTrue(app.contains("composable(ConnectionsRoute)"))
        assertTrue(app.contains("ConnectionsScreen("))
        assertTrue(app.contains("val settingsViewModel: SettingsViewModel = hiltViewModel()"))
        assertTrue(app.split("viewModel = settingsViewModel").size == 3)
        assertTrue(manifest.contains("<data android:host=\"connections\" />"))
    }

    @Test
    fun `account management is separate from app settings`() {
        val source = sourceFile("feature/settings/SettingsScreen.kt")
        val settingsStart = source.indexOf("fun SettingsScreen(")
        val connectionsStart = source.indexOf("fun ConnectionsScreen(")
        val helperStart = source.indexOf("private fun SettingsSectionHeader(")
        val settings = source.substring(settingsStart, connectionsStart)
        val connections = source.substring(connectionsStart, helperStart)

        assertTrue(settings.contains("NotificationsSection("))
        assertTrue(settings.contains("PrivacySection("))
        assertFalse(settings.contains("ProviderDirectoryControls("))
        assertFalse(settings.contains("ServiceCredentialSection("))
        assertFalse(settings.contains("DangerZoneSection("))

        assertTrue(connections.contains("ConnectionSummaryCard("))
        assertTrue(connections.contains("ProviderDirectoryControls("))
        assertTrue(connections.contains("ServiceCredentialSection("))
        assertTrue(connections.contains("DangerZoneSection("))
    }

    @Test
    fun `connection entry points do not send users back to settings`() {
        val dashboard = sourceFile("feature/dashboard/DashboardScreen.kt")
        val widget = sourceFile("core/widget/WidgetConfigurationActivity.kt")

        assertTrue(dashboard.contains("onNavigateToConnections"))
        assertTrue(dashboard.contains("R.string.action_open_connections"))
        assertFalse(dashboard.contains("onNavigateToSettings"))
        assertTrue(widget.contains("Uri.parse(\"codexbar://connections\")"))
        assertFalse(widget.contains("Uri.parse(\"codexbar://settings\")"))
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            appDir,
            "src/main/java/com/codexbar/android/$relativePath"
        ).readText().replace("\r\n", "\n")
    }
}
