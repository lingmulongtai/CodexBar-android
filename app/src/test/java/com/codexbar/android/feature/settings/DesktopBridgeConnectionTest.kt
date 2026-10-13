package com.codexbar.android.feature.settings

import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.network.desktop.DesktopBridgeClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopBridgeConnectionTest {
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
