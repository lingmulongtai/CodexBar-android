package com.codexbar.android.core.domain.model

import org.junit.Assert.*
import org.junit.Test

class AcquisitionSettingsTest {
    @Test fun `legacy selections preserve every existing service source`() {
        val selected = setOf(AiService.CODEX, AiService.CLAUDE)
        val settings = AcquisitionSettings.restore(true, null, selected, null, null)
        AiService.entries.forEach { assertEquals(it in selected, settings.routesToDesktop(it)) }
        assertEquals(selected, settings.desktopServices)
    }

    @Test fun `phone mode retains pairing selections while using only phone credentials`() {
        val settings = AcquisitionSettings(AcquisitionMode.PHONE, setOf(AiService.CODEX))
        assertFalse(settings.routesToDesktop(AiService.CODEX))
        assertTrue(settings.hasSource(AiService.CODEX, true))
        assertFalse(settings.hasSource(AiService.CODEX, false))
        assertEquals(setOf(AiService.CODEX), settings.advertisedServices)
    }

    @Test fun `windows mode does not use saved phone credentials when PC omits a service`() {
        val settings = AcquisitionSettings(AcquisitionMode.WINDOWS, setOf(AiService.CODEX))
        assertTrue(settings.routesToDesktop(AiService.CLAUDE))
        assertFalse(settings.hasSource(AiService.CLAUDE, true))
        assertTrue(settings.hasSource(AiService.CODEX, false))
        assertTrue(settings.copy(advertisedServices = setOf(AiService.CODEX, AiService.CLAUDE)).hasSource(AiService.CLAUDE, false))
    }

    @Test fun `explicit phone exceptions win without erasing PC availability`() {
        val settings = AcquisitionSettings(AcquisitionMode.WINDOWS, setOf(AiService.CODEX), setOf(AiService.CODEX))
        assertFalse(settings.routesToDesktop(AiService.CODEX))
        assertTrue(settings.hasSource(AiService.CODEX, true))
        assertFalse(settings.hasSource(AiService.CODEX, false))
        assertEquals(setOf(AiService.CODEX), settings.advertisedServices)
    }

    @Test fun `missing pairing or corrupt mode cannot activate Windows acquisition`() {
        assertEquals(AcquisitionMode.PHONE, AcquisitionSettings.restore(false, "WINDOWS", setOf(AiService.CODEX), null, null).mode)
        assertEquals(AcquisitionMode.PHONE, AcquisitionSettings.restore(true, "unknown", setOf(AiService.CODEX), null, null).mode)
    }
}
