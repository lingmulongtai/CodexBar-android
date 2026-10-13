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
        val source = DesktopQuotaSource(client, prefs, mock(QuotaResetMonitor::class.java))
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
        val source = DesktopQuotaSource(client, prefs, mock(QuotaResetMonitor::class.java))
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
