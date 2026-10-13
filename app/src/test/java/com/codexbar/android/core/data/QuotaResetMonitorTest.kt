package com.codexbar.android.core.data

import com.codexbar.android.core.auth.resetTrackingAccountKey
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.QuotaResetActivity
import com.codexbar.android.core.domain.model.QuotaResetKind
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import com.codexbar.android.core.domain.model.DesktopBridgeCredential
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.*

class QuotaResetMonitorTest {
    @Test fun `source switch discards old publication and preserves native credentials`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        val store = mock(QuotaResetHistoryStore::class.java)
        val notices = mock(QuotaNotificationService::class.java)
        val monitor = QuotaResetMonitor(store, prefs, notices)
        val old = monitor.sourceRevision()
        val desktop = DesktopBridgeCredential("127.0.0.1", 43825, "5b017391-6dc4-4ab7-b0ad-2255dada62d7", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        monitor.updateDesktopSource(desktop, setOf(AiService.CODEX)) {}
        var published = false
        assertFalse(monitor.consumeIfCurrent(old) { published = true })
        assertFalse(published)
        verify(prefs).saveDesktopBridge(desktop, setOf(AiService.CODEX))
        AiService.entries.forEach { verify(prefs, never()).deleteCredential(it) }
        verify(store).deleteService(AiService.CODEX)
    }

    @Test fun `obsolete pairing is rejected after waiting for publication lock`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        val monitor = QuotaResetMonitor(mock(QuotaResetHistoryStore::class.java), prefs, mock(QuotaNotificationService::class.java))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val valid = AtomicBoolean(true)
        val publisher = launch { monitor.consumeIfCurrent(0) { entered.complete(Unit); release.await() } }
        entered.await()
        var applied = true
        val pairing = launch { applied = monitor.updateDesktopSource(null, emptySet(), valid = valid::get) {} }
        valid.set(false)
        release.complete(Unit)
        publisher.join(); pairing.join()
        assertFalse(applied)
        verifyNoInteractions(prefs)
    }

    @Test fun `delete all invalidates results even when a response arrives later`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        val monitor = QuotaResetMonitor(mock(QuotaResetHistoryStore::class.java), prefs, mock(QuotaNotificationService::class.java))
        val before = monitor.sourceRevision()
        monitor.deleteAllSources {}
        assertFalse(monitor.consumeIfCurrent(before) { fail("Old snapshot was published") })
        verify(prefs).deleteAllCredentials()
        assertEquals(monitor.sourceRevision(), monitor.sourceChanges.value)
    }
    private lateinit var color: MockedStatic<android.graphics.Color>

    @Before fun mockAndroidColor() {
        color = mockStatic(android.graphics.Color::class.java)
        color.`when`<Int> { android.graphics.Color.rgb(anyInt(), anyInt(), anyInt()) }
            .thenAnswer { call -> (0xFF shl 24) or (call.getArgument<Int>(0) shl 16) or
                (call.getArgument<Int>(1) shl 8) or call.getArgument<Int>(2) }
    }

    @After fun closeAndroidColor() { color.close() }

    private val credential = Credential.CodexCredential("access", "refresh", accountId = "account")
    private val quota = QuotaInfo(AiService.CODEX, emptyList(), null, fetchedAt = Instant.now(),
        resetTrackingAccountKey = credential.resetTrackingAccountKey())
    private val event = QuotaResetActivity(QuotaResetKind.CREDITS_ADDED, quota.fetchedAt.toEpochMilli(), count = 1)

    @Test fun `reset alerts work independently from persistent notification`() = runTest {
        val store = mock(QuotaResetHistoryStore::class.java)
        val prefs = mock(EncryptedPrefsManager::class.java)
        val notifications = mock(QuotaNotificationService::class.java)
        `when`(prefs.loadCredential(AiService.CODEX)).thenReturn(credential)
        `when`(prefs.isResetNotificationEnabled()).thenReturn(true)
        `when`(store.record(listOf(quota))).thenReturn(mapOf(AiService.CODEX to listOf(event)))
        QuotaResetMonitor(store, prefs, notifications).observe(listOf(quota))
        verify(notifications).showResetActivityNotification(AiService.CODEX, event)
        verify(prefs, never()).isPersistentNotificationEnabled()
    }

    @Test fun `disabled alerts still record history`() = runTest {
        val store = mock(QuotaResetHistoryStore::class.java)
        val prefs = mock(EncryptedPrefsManager::class.java)
        val notifications = mock(QuotaNotificationService::class.java)
        `when`(prefs.loadCredential(AiService.CODEX)).thenReturn(credential)
        `when`(store.record(listOf(quota))).thenReturn(mapOf(AiService.CODEX to listOf(event)))
        QuotaResetMonitor(store, prefs, notifications).observe(listOf(quota))
        verify(store).record(listOf(quota))
        verifyNoInteractions(notifications)
    }

    @Test fun `disconnected account and old account responses are ignored`() = runTest {
        val store = mock(QuotaResetHistoryStore::class.java)
        val prefs = mock(EncryptedPrefsManager::class.java)
        val notifications = mock(QuotaNotificationService::class.java)
        `when`(store.record(emptyList())).thenReturn(emptyMap())
        val monitor = QuotaResetMonitor(store, prefs, notifications)
        monitor.observe(listOf(quota))
        `when`(prefs.loadCredential(AiService.CODEX)).thenReturn(credential.copy(accountId = "other"))
        monitor.observe(listOf(quota))
        verify(store, times(2)).record(emptyList())
        verifyNoInteractions(notifications)
    }

    @Test fun `replacement account clears history before its first refresh even if refresh fails`() = runTest {
        val store = mock(QuotaResetHistoryStore::class.java)
        val prefs = mock(EncryptedPrefsManager::class.java)
        val notifications = mock(QuotaNotificationService::class.java)
        `when`(prefs.loadCredential(AiService.CODEX)).thenReturn(credential)
        `when`(store.record(emptyList())).thenReturn(emptyMap())
        val replacement = credential.copy(accountId = "other")
        val monitor = QuotaResetMonitor(store, prefs, notifications)
        monitor.saveCredential(AiService.CODEX, replacement)
        monitor.observe(emptyList())
        val order = inOrder(prefs, store, notifications)
        order.verify(prefs).saveCredential(AiService.CODEX, replacement)
        order.verify(store).deleteService(AiService.CODEX)
        order.verify(notifications).cancelResetNotifications(AiService.CODEX)
        order.verify(store).record(emptyList())
    }

    @Test fun `same account token replacement preserves history`() = runTest {
        val store = mock(QuotaResetHistoryStore::class.java)
        val prefs = mock(EncryptedPrefsManager::class.java)
        val notifications = mock(QuotaNotificationService::class.java)
        `when`(prefs.loadCredential(AiService.CODEX)).thenReturn(credential)
        QuotaResetMonitor(store, prefs, notifications).saveCredential(AiService.CODEX,
            credential.copy(accessToken = "new", refreshToken = "rotated"))
        verifyNoInteractions(store, notifications)
    }
}
