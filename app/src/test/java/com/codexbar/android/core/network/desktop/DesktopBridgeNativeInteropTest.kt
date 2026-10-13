package com.codexbar.android.core.network.desktop

import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional proof against the actual Windows executable with synthetic usage. */
class DesktopBridgeNativeInteropTest {
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
    }
}
