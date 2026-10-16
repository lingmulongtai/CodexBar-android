package com.codexbar.android.core.network.desktop

import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional proof against the actual Windows executable with synthetic usage. */
class DesktopBridgeNativeInteropTest {
    @Test fun `two devices cannot overwrite the same native Windows settings revision`() = runTest {
        val path = System.getenv("CBWIN_BRIDGE_REMOTE_TEST_CODE_PATH")
        assumeTrue("Run with an isolated opt-in native Windows proof profile", path != null)
        val credential = DesktopBridgePairing.parse(File(checkNotNull(path)).readText())
        val client = DesktopBridgeClient(Json { ignoreUnknownKeys = true })
        val before = client.remoteSettings(credential).settings
        assertTrue(before.allowed)
        try {
            val results = coroutineScope {
                listOf(600L to "rolling:7", 900L to "rolling:90").map { (interval, period) ->
                    async { client.updateRemoteSettings(credential, DesktopRemoteSettingsUpdate(before.revision, interval, period)) }
                }.awaitAll()
            }
            assertEquals(1, results.count { it.status == "ok" })
            assertEquals(1, results.count { it.status == "conflict" })
            assertEquals(results[0].settings, results[1].settings)
        } finally {
            val current = client.remoteSettings(credential).settings
            assertEquals("ok", client.updateRemoteSettings(credential,
                DesktopRemoteSettingsUpdate(current.revision, before.refreshIntervalSecs, before.reportingPeriod)).status)
        }
    }
    @Test fun `decrypts a real Windows desktop snapshot`() = runTest {
        val path = System.getenv("CBWIN_BRIDGE_TEST_CODE_PATH")
        assumeTrue("Run with an isolated native Windows proof profile", path != null)
        val credential = DesktopBridgePairing.parse(File(checkNotNull(path)).readText())
        val snapshot = DesktopBridgeClient(Json { ignoreUnknownKeys = true }).fetchSnapshot(credential)
        val codex = snapshot.providers.single { it.id == "codex" }
        assertEquals("ready", codex.status)
        assertEquals(0.25, codex.windows.first().usedFraction, 0.0)
        assertEquals(300L * 60, codex.windows.first().windowDurationSeconds)
        assertNull(codex.subjectId)
        val local = checkNotNull(codex.localUsage)
        assertEquals(12500L, local.todayTokens)
        assertEquals(250000L, local.thirtyDayTokens)
        assertEquals(0.25, checkNotNull(local.todayCostUsd), 0.0)
    }
}
