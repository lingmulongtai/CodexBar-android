package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.network.desktop.*
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class DesktopQuotaSourceTest {
    @Test fun `Gemini Apps has its own id and preserves both quota windows`() {
        assertEquals(AiService.GEMINI_APPS, DesktopQuotaSource.serviceFor("geminiapps"))
        assertEquals(AiService.GEMINI, DesktopQuotaSource.serviceFor("gemini"))
        val now = Instant.ofEpochSecond(1800000000)
        val provider = DesktopProvider("geminiapps", now.epochSecond, "ready", windows = listOf(
            DesktopWindow("Current", 0.0, 1800018000, 18000),
            DesktopWindow("Weekly", 0.01084324, 1800604800, 604800)
        ))
        val quota = with(DesktopQuotaSource) { provider.toQuota(AiService.GEMINI_APPS, "pairing", now) }
        assertEquals(0.0, quota.windows[0].utilization, 0.0)
        assertEquals(0.01084324, quota.windows[1].utilization, 0.000000001)
        assertEquals(Instant.ofEpochSecond(1800018000), quota.windows[0].resetsAt)
        assertFalse(quota.historyEligible)
        assertNull(quota.resetTrackingAccountKey)
    }
    @Test fun `bulk refresh discovers unavailable providers while preserving phone exceptions`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        val credential = DesktopBridgeCredential("100.64.1.2", 43825, "id", "key")
        var settings = AcquisitionSettings(AcquisitionMode.WINDOWS, setOf(AiService.CODEX), setOf(AiService.CLAUDE))
        var revision = 0L
        `when`(prefs.desktopBridgeCredential()).thenReturn(credential)
        `when`(prefs.acquisitionSettings()).thenAnswer { settings }
        val monitor = mock(QuotaResetMonitor::class.java)
        `when`(monitor.sourceRevision()).thenAnswer { revision }
        val coordinator = mock(DesktopSourceCoordinator::class.java)
        `when`(coordinator.update(any(), any() ?: settings, any() ?: { true })).thenAnswer { call ->
            if (call.getArgument<() -> Boolean>(2).invoke()) {
                settings = call.getArgument(1); revision++; true
            } else false
        }
        var calls = 0
        val client = object : DesktopBridgeClient(Json) {
            override suspend fun fetchSnapshot(credential: DesktopBridgeCredential, now: Instant): DesktopBridgeSnapshot {
                calls++
                return DesktopBridgeSnapshot(1, "win-codexbar", now.epochSecond, listOf(
                    DesktopProvider("codex", now.epochSecond, "ready", windows = listOf(DesktopWindow("Session", 0.2))),
                    DesktopProvider("claude", now.epochSecond, "needsAuthentication", windows = emptyList()),
                    DesktopProvider("gemini", now.epochSecond, "unavailable", windows = emptyList())))
            }
        }
        val source = DesktopQuotaSource(client, prefs, monitor, coordinator)
        assertEquals(1L, source.prepareRefresh())
        assertEquals(setOf(AiService.CODEX, AiService.CLAUDE, AiService.GEMINI), settings.advertisedServices)
        assertEquals(setOf(AiService.CLAUDE), settings.phoneExceptions)
        source.fetchQuota(AiService.CODEX)
        assertEquals(1, calls)
        settings = settings.copy(mode = AcquisitionMode.PHONE)
        source.prepareRefresh()
        assertEquals(1, calls)
    }

    @Test fun `canonical Windows ids distinguish Kimi Coding from Moonshot API`() {
        assertEquals(AiService.KIMI, DesktopQuotaSource.serviceFor("kimi"))
        assertEquals(AiService.MOONSHOT, DesktopQuotaSource.serviceFor("kimik2"))
        assertEquals(AiService.CLINEPASS, DesktopQuotaSource.serviceFor("clinepass"))
    }
    @Test fun `providers share one fetch and a different pairing cannot reuse it`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        val credential = DesktopBridgeCredential("127.0.0.1", 43825, "5b017391-6dc4-4ab7-b0ad-2255dada62d7", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        `when`(prefs.desktopBridgeCredential()).thenReturn(credential)
        var calls = 0
        val client = object : DesktopBridgeClient(Json) {
            override suspend fun fetchSnapshot(credential: DesktopBridgeCredential, now: Instant): DesktopBridgeSnapshot {
                calls++
                return DesktopBridgeSnapshot(1, "win-codexbar", now.epochSecond,
                    listOf("codex", "claude").map { DesktopProvider(it, now.epochSecond, "ready", windows = listOf(DesktopWindow("Session", 0.25)), resetCredits = DesktopResetCredits(0, emptyList())) })
            }
        }
        val source = DesktopQuotaSource(client, prefs, mock(QuotaResetMonitor::class.java), mock(DesktopSourceCoordinator::class.java))
        assertTrue(source.fetchQuota(AiService.CODEX) is Result.Success)
        assertTrue(source.fetchQuota(AiService.CLAUDE) is Result.Success)
        assertEquals(1, calls)
        `when`(prefs.desktopBridgeCredential()).thenReturn(credential.copy(sharedKeyBase64Url = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE"))
        source.fetchQuota(AiService.CODEX)
        assertEquals(2, calls)
    }

    @Test fun `offline Windows fails once for the whole provider batch`() = runTest {
        val prefs = mock(EncryptedPrefsManager::class.java)
        `when`(prefs.desktopBridgeCredential()).thenReturn(DesktopBridgeCredential("127.0.0.1", 43825, "id", "key"))
        var calls = 0
        val client = object : DesktopBridgeClient(Json) {
            override suspend fun fetchSnapshot(credential: DesktopBridgeCredential, now: Instant): DesktopBridgeSnapshot {
                calls++
                throw java.io.IOException("offline")
            }
        }
        val source = DesktopQuotaSource(client, prefs, mock(QuotaResetMonitor::class.java), mock(DesktopSourceCoordinator::class.java))
        assertTrue(source.fetchQuota(AiService.CODEX) is Result.Failure)
        assertTrue(source.fetchQuota(AiService.CLAUDE) is Result.Failure)
        assertEquals(1, calls)
    }

    @Test fun `maps real observation time and unknown reset credits remain unknown`() {
        val known = DesktopProvider("codex", 1750000000, "ready", subjectId = "opaque-account", windows = listOf(DesktopWindow("Weekly", 0.4, windowDurationSeconds = 604800)), resetCredits = DesktopResetCredits(0, emptyList()))
        val quota = with(DesktopQuotaSource) { known.toQuota(AiService.CODEX, "pairing") }
        assertEquals(Instant.ofEpochSecond(1750000000), quota.fetchedAt)
        assertEquals(604800L, quota.windows.single().windowDurationSeconds)
        assertEquals(0, quota.codexResetCredits?.availableCount)
        assertEquals("desktop:pairing:opaque-account", quota.resetTrackingAccountKey)
        assertNull(with(DesktopQuotaSource) { known.copy(resetCredits = null).toQuota(AiService.CODEX, "pairing") }.codexResetCredits)
        assertTrue(with(DesktopQuotaSource) { known.toQuota(AiService.CODEX, "pairing", Instant.ofEpochSecond(1750000000 + 5400)) }.isStale)
        assertFalse(with(DesktopQuotaSource) { known.toQuota(AiService.CODEX, "pairing", Instant.ofEpochSecond(1750000000 + 600)) }.isStale)
    }
}
