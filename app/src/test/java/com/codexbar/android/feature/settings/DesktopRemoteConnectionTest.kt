package com.codexbar.android.feature.settings

import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.data.*
import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.network.desktop.*
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.*
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopRemoteConnectionTest {
    private val a = DesktopBridgeCredential("100.64.1.2",43825,"a","key-a")
    private val settings = DesktopRemoteSettingsResult("ok", DesktopRemoteSettings(true,"a".repeat(64),300,false,"rolling:30"))

    @Test fun `a draft from one PC cannot be sent to another before source change delivery`() = runTest {
        val client=mock(DesktopBridgeClient::class.java)
        `when`(client.remoteSettings(a)).thenReturn(settings)
        Fixture(this,client).use { fixture ->
            fixture.connection.loadRemoteSettings(); advanceUntilIdle()
            assertNotNull(fixture.connection.state.value.remoteSettings)
            fixture.credential=a.copy(companionId="b")
            fixture.connection.updateRemoteSettings(600,null); advanceUntilIdle()
            assertNull(fixture.connection.state.value.remoteSettings)
            verify(client, never()).updateRemoteSettings(any() ?: a, any() ?: DesktopRemoteSettingsUpdate("a".repeat(64),600))
        }
    }

    @Test fun `a late failed response is discarded after pairing replacement`() = runTest {
        val response=CompletableDeferred<DesktopRemoteSettingsResult>()
        val client=object:DesktopBridgeClient(kotlinx.serialization.json.Json) {
            override suspend fun remoteSettings(credential:DesktopBridgeCredential)=response.await()
        }
        Fixture(this,client).use { fixture ->
            fixture.connection.loadRemoteSettings(); runCurrent()
            fixture.credential=a.copy(companionId="b")
            fixture.connection.syncSaved()
            response.completeExceptionally(IOException("old PC")); advanceUntilIdle()
            assertFalse(fixture.connection.state.value.busy)
            assertNull(fixture.connection.state.value.result)
            assertNull(fixture.connection.state.value.remoteSettings)
        }
    }

    @Test fun `the previous saved message is cleared before the next failed operation`() = runTest {
        val client=mock(DesktopBridgeClient::class.java)
        `when`(client.remoteSettings(a)).thenReturn(settings)
        val update=DesktopRemoteSettingsUpdate(settings.settings.revision,600,null)
        `when`(client.updateRemoteSettings(a,update)).thenReturn(settings.copy(settings=settings.settings.copy(refreshIntervalSecs=600,revision="b".repeat(64))))
        Fixture(this,client).use { fixture ->
            fixture.connection.loadRemoteSettings(); advanceUntilIdle()
            fixture.connection.updateRemoteSettings(600,null); advanceUntilIdle()
            assertEquals("Saved",fixture.connection.state.value.remoteMessage)
            `when`(client.remoteSettings(a)).thenAnswer { throw IOException("offline") }
            fixture.connection.loadRemoteSettings(); advanceUntilIdle()
            assertNull(fixture.connection.state.value.remoteMessage)
            assertTrue(fixture.connection.state.value.result is ValidationResult.Failure)
        }
    }

    private inner class Fixture(scope:CoroutineScope,client:DesktopBridgeClient):AutoCloseable {
        private val color=mockStatic(android.graphics.Color::class.java)
        var credential=a
        private val prefs=mock(EncryptedPrefsManager::class.java)
        private val context=mock(Context::class.java)
        val connection:DesktopBridgeConnection
        init {
            `when`(prefs.desktopBridgeCredential()).thenAnswer { credential }
            `when`(prefs.acquisitionSettings()).thenReturn(AcquisitionSettings(AcquisitionMode.WINDOWS,setOf(AiService.CODEX)))
            `when`(context.getString(R.string.desktop_remote_saved)).thenReturn("Saved")
            `when`(context.getString(R.string.desktop_connection_failed)).thenReturn("Offline")
            connection=DesktopBridgeConnection(scope,client,prefs,mock(QuotaResetMonitor::class.java),mock(ConnectionHealthStore::class.java),
                mock(QuotaHistoryStore::class.java),mock(WidgetPrefsManager::class.java),mock(MonitoringSessionStore::class.java),mock(QuotaNotificationService::class.java),context)
            connection.syncSaved()
        }
        override fun close() { connection.cancelPending(); color.close() }
    }
}
