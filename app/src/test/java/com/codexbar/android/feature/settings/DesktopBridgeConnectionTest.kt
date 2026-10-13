package com.codexbar.android.feature.settings

import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.network.desktop.DesktopBridgeClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopBridgeConnectionTest {
    @Test fun `mode round trips preserve pairing and exceptions until explicitly reset`() = runTest {
        val color = mockStatic(android.graphics.Color::class.java)
        val work = mockStatic(androidx.work.WorkManager::class.java)
        try {
            val context = mock(Context::class.java)
            work.`when`<androidx.work.WorkManager> { androidx.work.WorkManager.getInstance(context) }
                .thenReturn(mock(androidx.work.WorkManager::class.java))
            val prefs = mock(com.codexbar.android.core.security.EncryptedPrefsManager::class.java)
            val credential = com.codexbar.android.core.domain.model.DesktopBridgeCredential("100.64.1.2", 43825, "id", "key")
            var settings = com.codexbar.android.core.domain.model.AcquisitionSettings(
                com.codexbar.android.core.domain.model.AcquisitionMode.WINDOWS,
                setOf(com.codexbar.android.core.domain.model.AiService.CODEX),
                setOf(com.codexbar.android.core.domain.model.AiService.CLAUDE))
            `when`(prefs.desktopBridgeCredential()).thenReturn(credential)
            `when`(prefs.acquisitionSettings()).thenAnswer { settings }
            `when`(prefs.saveDesktopSource(any(), any() ?: settings)).thenAnswer { settings = it.getArgument(1); Unit }
            val notices = mock(com.codexbar.android.core.notification.QuotaNotificationService::class.java)
            val monitor = com.codexbar.android.core.data.QuotaResetMonitor(
                mock(com.codexbar.android.core.data.QuotaResetHistoryStore::class.java), prefs, notices)
            val connection = DesktopBridgeConnection(this, mock(DesktopBridgeClient::class.java), prefs, monitor,
                mock(com.codexbar.android.core.security.ConnectionHealthStore::class.java),
                mock(com.codexbar.android.core.data.QuotaHistoryStore::class.java),
                mock(com.codexbar.android.core.widget.WidgetPrefsManager::class.java),
                mock(com.codexbar.android.core.monitoring.MonitoringSessionStore::class.java), notices, context)
            connection.syncSaved()
            connection.setMode(com.codexbar.android.core.domain.model.AcquisitionMode.PHONE)
            advanceUntilIdle()
            connection.state.first { !it.busy }
            connection.setMode(com.codexbar.android.core.domain.model.AcquisitionMode.WINDOWS)
            advanceUntilIdle()
            connection.state.first { !it.busy }
            assertEquals(setOf(com.codexbar.android.core.domain.model.AiService.CLAUDE), settings.phoneExceptions)
            assertEquals(setOf(com.codexbar.android.core.domain.model.AiService.CODEX), settings.advertisedServices)
            assertEquals(credential, prefs.desktopBridgeCredential())
            connection.clearPhoneExceptions()
            advanceUntilIdle()
            connection.state.first { !it.busy }
            assertEquals(emptySet<com.codexbar.android.core.domain.model.AiService>(), settings.phoneExceptions)
        } finally { work.close(); color.close() }
    }
    @Test fun `invalid pairing explains the format error without starting a network request`() = runTest {
        val color = mockStatic(android.graphics.Color::class.java)
        try {
            val client = mock(DesktopBridgeClient::class.java)
            val context = mock(Context::class.java)
            `when`(context.getString(R.string.desktop_pairing_invalid)).thenReturn("Invalid pairing code")
            val connection = DesktopBridgeConnection(
                this, client,
                mock(com.codexbar.android.core.security.EncryptedPrefsManager::class.java),
                mock(com.codexbar.android.core.data.QuotaResetMonitor::class.java),
                mock(com.codexbar.android.core.security.ConnectionHealthStore::class.java),
                mock(com.codexbar.android.core.data.QuotaHistoryStore::class.java),
                mock(com.codexbar.android.core.widget.WidgetPrefsManager::class.java),
                mock(com.codexbar.android.core.monitoring.MonitoringSessionStore::class.java),
                mock(com.codexbar.android.core.notification.QuotaNotificationService::class.java), context
            )
            connection.updateCode("invalid")
            connection.connect()
            advanceUntilIdle()
            assertEquals(ValidationResult.Failure("Invalid pairing code"), connection.state.value.result)
            assertFalse(connection.state.value.busy)
            verifyNoInteractions(client)
        } finally { color.close() }
    }
}
