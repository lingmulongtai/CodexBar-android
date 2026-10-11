package com.codexbar.android.core.widget

import androidx.compose.runtime.saveable.SaverScope
import com.codexbar.android.core.domain.model.AiService
import org.junit.Assert.*
import org.junit.Test

class WidgetSelectionPolicyTest {
    @Test fun `disconnected Antigravity stays editable until removed`() {
        val connected = listOf(AiService.CODEX)
        val configured = listOf(AiService.CODEX, AiService.ANTIGRAVITY)
        val choices = WidgetSelectionPolicy.choices(connected, configured)
        assertTrue(AiService.ANTIGRAVITY in choices)
        assertEquals(configured, WidgetSelectionPolicy.initialSelection(connected, configured, true))
        val selected = configured - AiService.ANTIGRAVITY
        assertTrue(WidgetSelectionPolicy.canSave(selected, true))
        assertEquals(listOf(AiService.CODEX), selected)
        assertFalse(AiService.ANTIGRAVITY in WidgetSelectionPolicy.choices(connected, selected))
    }

    @Test fun `only disconnected provider can be removed without reconnecting`() {
        val configured = listOf(AiService.ANTIGRAVITY)
        assertEquals(configured, WidgetSelectionPolicy.choices(emptyList(), configured))
        assertTrue(WidgetSelectionPolicy.canSave(emptyList(), true))
        assertTrue(WidgetSelectionPolicy.initialSelection(listOf(AiService.CODEX), emptyList(), true).isEmpty())
    }

    @Test fun `new widgets default to connected providers and require a selection`() {
        val connected = listOf(AiService.CODEX, AiService.CLAUDE)
        assertEquals(connected, WidgetSelectionPolicy.initialSelection(connected, emptyList(), false))
        assertFalse(WidgetSelectionPolicy.canSave(emptyList(), false))
        assertTrue(WidgetSelectionPolicy.canSave(connected, false))
    }

    @Test fun `rotation keeps deliberate empty selection and appearance`() {
        val config = WidgetDisplayConfig(services = emptyList(), showReset = false, maxRows = 2,
            style = WidgetStyle(opacity = 37))
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(WidgetConfigSaver) { scope.save(config) }!!
        assertEquals(config, WidgetConfigSaver.restore(saved))
    }
}
