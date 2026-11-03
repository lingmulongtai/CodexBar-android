package com.codexbar.android.core.update

import org.junit.Assert.*
import org.junit.Test

class UpdateDownloadStateTest {
    @Test fun `all received bytes do not claim completion before verification`() {
        val downloaded = UpdateDownloadState(UpdateDownloadPhase.DOWNLOADING, received = 1000, total = 1000)
        assertEquals(99, downloaded.percent)
        assertTrue(downloaded.busy)
        assertTrue(downloaded.copy(phase = UpdateDownloadPhase.VERIFYING).busy)
        val ready = downloaded.copy(phase = UpdateDownloadPhase.READY)
        assertEquals(100, ready.percent)
        assertFalse(ready.busy)
    }
}
