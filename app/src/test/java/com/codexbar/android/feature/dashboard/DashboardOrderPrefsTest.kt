package com.codexbar.android.feature.dashboard

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class DashboardOrderPrefsTest {
    @Test fun `page orders survive recreation and resets affect only their own page`() {
        val stored = mutableMapOf("provider_order" to "CLAUDE,CODEX")
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(context.getSharedPreferences("dashboard_display", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { stored[it.getArgument<String>(0)] }
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            stored[it.getArgument(0)] = it.getArgument(1); editor
        }
        val first = DashboardOrderPrefs(context)
        assertEquals(listOf(CLAUDE, CODEX), first.load())
        first.save(listOf(CODEX, CLAUDE), ProviderOrderScope.TRENDS)
        first.save(listOf(GEMINI, CODEX), ProviderOrderScope.CONNECTIONS)
        val restored = DashboardOrderPrefs(context)
        assertEquals(listOf(CLAUDE, CODEX), restored.load())
        assertEquals(listOf(CODEX, CLAUDE), restored.load(ProviderOrderScope.TRENDS))
        assertEquals(listOf(GEMINI, CODEX), restored.load(ProviderOrderScope.CONNECTIONS))
        restored.save(emptyList(), ProviderOrderScope.TRENDS)
        assertEquals(emptyList<Any>(), restored.load(ProviderOrderScope.TRENDS))
        assertEquals(listOf(GEMINI, CODEX), restored.load(ProviderOrderScope.CONNECTIONS))
        assertEquals(listOf(CLAUDE, CODEX), restored.load())
    }
}
