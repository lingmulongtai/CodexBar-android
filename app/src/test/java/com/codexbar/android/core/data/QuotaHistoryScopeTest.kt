package com.codexbar.android.core.data

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class QuotaHistoryScopeTest {
    @Test fun `Windows series replace local observations identically across polling times`() {
        val now = Instant.now()
        val samples = listOf(
            com.codexbar.android.core.domain.model.DesktopQuotaSample(now.minusSeconds(600), 0.1, null),
            com.codexbar.android.core.domain.model.DesktopQuotaSample(now.minusSeconds(300), 0.2, null))
        val quota = QuotaInfo(AiService.GEMINI_APPS, listOf(UsageWindow("Current", 0.2, null)), null,
            fetchedAt = now, historyEligible = false, isDesktopSource = true,
            desktopHistoryScopeKey = "desktop:pc:history:connection-a",
            desktopQuotaHistory = listOf(com.codexbar.android.core.domain.model.DesktopQuotaSeries("Current", samples)))
        val phone = QuotaHistoryStore(contextFor(mutableMapOf()))
        val tablet = QuotaHistoryStore(contextFor(mutableMapOf()))
        phone.record(listOf(quota))
        phone.record(listOf(quota.copy(fetchedAt = now.plusSeconds(30))))
        tablet.record(listOf(quota.copy(fetchedAt = now.plusSeconds(60))))
        assertEquals(2, phone.historyFor(listOf(quota)).values.single().size)
        assertEquals(phone.historyFor(listOf(quota)), tablet.historyFor(listOf(quota)))
        val anotherConnection = quota.copy(desktopHistoryScopeKey = "desktop:pc:history:connection-b", desktopQuotaHistory = emptyList())
        tablet.record(listOf(anotherConnection))
        assertTrue(tablet.historyFor(listOf(anotherConnection)).values.single().isEmpty())
    }
    @Test fun `legacy owner survives a different account and expired keys retain fresh writes`() {
        val now = Instant.now()
        val values = mutableMapOf<String, String?>(
            "scope:CODEX" to "B",
            "CODEX:Session" to "${now.minusSeconds(60).toEpochMilli()}|0.2|",
            "CODEX:scoped:${historyScope("A")}:Session" to "${now.minusSeconds(91L * 86400).toEpochMilli()}|0.1|"
        )
        val store = QuotaHistoryStore(contextFor(values))
        val a = QuotaInfo(AiService.CODEX, listOf(UsageWindow("Session", 0.3, null)), null,
            fetchedAt = now, resetTrackingAccountKey = "A")
        store.record(listOf(a))
        store.record(listOf(a.copy(fetchedAt = now.plusSeconds(30))))
        assertEquals(2, store.historyFor(listOf(a)).values.single().size)
        assertEquals("B", values["scope:CODEX"])
        val b = a.copy(fetchedAt = now.plusSeconds(60), resetTrackingAccountKey = "B")
        store.record(listOf(b))
        assertEquals(2, store.historyFor(listOf(b)).values.single().size)
        assertEquals(2, store.historyFor(listOf(a)).values.single().size)
    }

    private fun contextFor(values: MutableMap<String, String?>): Context {
        val pending = mutableMapOf<String, String?>()
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenAnswer { pending.clear(); editor }
        `when`(prefs.all).thenAnswer { values.toMap() }
        `when`(prefs.contains(anyString())).thenAnswer { values.containsKey(it.getArgument<String>(0)) }
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { values[it.getArgument<String>(0)] }
        `when`(editor.putString(anyString(), any())).thenAnswer { pending[it.getArgument(0)] = it.getArgument(1); editor }
        `when`(editor.remove(anyString())).thenAnswer { pending[it.getArgument(0)] = null; editor }
        doAnswer {
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }; null
        }.`when`(editor).apply()
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(QuotaHistoryStore.PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(prefs)
        return context
    }

    @Test fun `account sources retain isolated samples across round trips without extending stale data`() {
        val values = mutableMapOf<String, String?>()
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.all).thenAnswer { values.toMap() }
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { values[it.getArgument<String>(0)] }
        `when`(editor.putString(anyString(), any())).thenAnswer { values[it.getArgument(0)] = it.getArgument(1); editor }
        `when`(editor.remove(anyString())).thenAnswer { values.remove(it.getArgument<String>(0)); editor }
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(QuotaHistoryStore.PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(prefs)
        val store = QuotaHistoryStore(context)
        val now = Instant.now()
        val first = QuotaInfo(AiService.CODEX, listOf(UsageWindow("Session", 0.1, now.plusSeconds(3600))), null,
            fetchedAt = now, resetTrackingAccountKey = "desktop:pair:account-A")
        store.record(listOf(first))
        store.record(listOf(first.copy(fetchedAt = now.plusSeconds(60))))
        assertEquals(2, store.historyFor(listOf(first)).values.single().size)
        val second = first.copy(fetchedAt = now.plusSeconds(120), resetTrackingAccountKey = "desktop:pair:account-B")
        store.record(listOf(second))
        assertEquals(1, store.historyFor(listOf(second)).values.single().size)
        store.record(listOf(first.copy(fetchedAt = now.plusSeconds(200))))
        assertEquals(3, store.historyFor(listOf(first)).values.single().size)
        assertEquals(1, store.historyFor(listOf(second)).values.single().size)
        val stale = second.copy(fetchedAt = now.plusSeconds(180), isStale = true)
        store.record(listOf(stale))
        assertTrue(store.historyFor(listOf(stale)).isEmpty())
        assertEquals(1, store.historyFor(listOf(second)).values.single().size)
        store.deleteService(AiService.CODEX)
        assertTrue(store.historyFor(listOf(first)).values.single().isEmpty())
        assertTrue(store.historyFor(listOf(second)).values.single().isEmpty())
    }
}
