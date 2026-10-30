package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.AiService.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardProviderOrderTest {
    @Test fun `stored order tolerates removed providers and duplicates`() {
        assertEquals(listOf(CODEX, CLAUDE), DashboardProviderOrder.decode("CODEX,obsolete,CLAUDE,CODEX"))
        assertEquals(emptyList<Any>(), DashboardProviderOrder.decode(null))
    }

    @Test fun `custom order survives refreshed usage ranking and appends new providers`() {
        val custom = listOf(CODEX, CLAUDE, COPILOT)
        assertEquals(listOf(CODEX, CLAUDE, COPILOT, GEMINI),
            DashboardProviderOrder.apply(listOf(COPILOT, GEMINI, CLAUDE, CODEX), custom) { it })
        assertEquals(listOf(CODEX, CLAUDE, COPILOT, GEMINI),
            DashboardProviderOrder.apply(listOf(GEMINI, CLAUDE, CODEX, COPILOT), custom) { it })
    }

    @Test fun `reordering connected providers retains disconnected provider position`() {
        val saved = listOf(CODEX, CLAUDE, COPILOT)
        val merged = DashboardProviderOrder.merge(saved, listOf(COPILOT, GEMINI, CODEX))
        assertEquals(listOf(COPILOT, CLAUDE, GEMINI, CODEX), merged)
        assertEquals(listOf(COPILOT, CLAUDE, GEMINI, CODEX),
            DashboardProviderOrder.apply(listOf(CODEX, GEMINI, CLAUDE, COPILOT), merged) { it })
    }

    @Test fun `empty order restores automatic usage ranking`() {
        val automatic = listOf(CLAUDE, COPILOT, CODEX)
        assertEquals(automatic, DashboardProviderOrder.apply(automatic, emptyList()) { it })
    }

    @Test fun `dragging filtered providers keeps hidden provider slots`() {
        val all = listOf(CODEX, CLAUDE, COPILOT, GEMINI)
        assertEquals(listOf(COPILOT, CLAUDE, CODEX, GEMINI),
            DashboardProviderOrder.merge(all, listOf(COPILOT, CODEX)))
        assertEquals(all, DashboardProviderOrder.merge(all, listOf(CODEX, COPILOT)))
    }

    @Test fun `editing a filtered list keeps disconnected and filtered providers`() {
        val saved = listOf(CODEX, CLAUDE, COPILOT, GEMINI)
        val displayed = listOf(CODEX, COPILOT, GEMINI)
        assertEquals(listOf(GEMINI, CLAUDE, COPILOT, CODEX),
            DashboardProviderOrder.reorder(saved, displayed, listOf(GEMINI, CODEX)))
        assertEquals(displayed, DashboardProviderOrder.reorder(emptyList(), displayed, displayed))
    }
}
