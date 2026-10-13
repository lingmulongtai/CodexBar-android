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
