package com.codexbar.android.core.data

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.CodexResetCredits
import com.codexbar.android.core.domain.model.QuotaInfo
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.*

class QuotaResetHistoryStoreTest {
    private val now = Instant.now()

    @Test fun `phone and PC retain separate histories and notification baselines when modes switch`() {
        val store = QuotaResetHistoryStore(fixture().context)
        val phone = quota(1).copy(resetTrackingAccountKey = "phone:account")
        val pc = quota(20, 2).copy(resetTrackingAccountKey = "desktop:pc:account")
        store.record(listOf(phone))
        store.record(listOf(phone.copy(fetchedAt = now.plusSeconds(1), codexResetCredits = CodexResetCredits(2, emptyList()))))
        val phoneHistory = store.history.value.getValue(AiService.CODEX)
        store.deactivate(AiService.CODEX)
        assertTrue(store.history.value.getValue(AiService.CODEX).isEmpty())
        assertTrue(store.record(listOf(pc)).isEmpty())
        store.record(listOf(pc.copy(fetchedAt = now.plusSeconds(3), codexResetCredits = CodexResetCredits(19, emptyList()))))
        val pcHistory = store.history.value.getValue(AiService.CODEX)
        store.deactivate(AiService.CODEX)
        assertTrue(store.record(listOf(phone.copy(fetchedAt = now.plusSeconds(4), codexResetCredits = CodexResetCredits(2, emptyList())))).isEmpty())
        assertEquals(phoneHistory, store.history.value.getValue(AiService.CODEX))
        store.deactivate(AiService.CODEX)
        assertTrue(store.record(listOf(pc.copy(fetchedAt = now.plusSeconds(5), codexResetCredits = CodexResetCredits(19, emptyList())))).isEmpty())
        assertEquals(pcHistory, store.history.value.getValue(AiService.CODEX))
        store.deleteService(AiService.CODEX)
        assertTrue(store.record(listOf(phone.copy(fetchedAt = now.plusSeconds(6)))).isEmpty())
    }

    @Test fun `restart retains history and notification baseline`() {
        val fixture = fixture()
        val store = QuotaResetHistoryStore(fixture.context)
        store.record(listOf(quota(1)))
        assertEquals(1, store.record(listOf(quota(2, 1))).getValue(AiService.CODEX).size)
        val restarted = QuotaResetHistoryStore(fixture.context)
        assertEquals(store.history.value, restarted.history.value)
        assertTrue(restarted.record(listOf(quota(2, 2))).isEmpty())
    }

    @Test fun `concurrent refreshes emit a single activity`() {
        val store = QuotaResetHistoryStore(fixture().context)
        store.record(listOf(quota(1)))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(List(2) { Callable { store.record(listOf(quota(2, 1))) } })
            assertEquals(1, results.sumOf { it.get(5, TimeUnit.SECONDS)[AiService.CODEX].orEmpty().size })
            assertEquals(1, store.history.value.getValue(AiService.CODEX).size)
        } finally { executor.shutdownNow() }
    }

    @Test fun `clearing visible history preserves notification baseline`() {
        val store = QuotaResetHistoryStore(fixture().context)
        store.record(listOf(quota(1)))
        store.record(listOf(quota(2, 1)))
        store.clearHistory(AiService.CODEX)
        assertTrue(store.history.value.getValue(AiService.CODEX).isEmpty())
        assertTrue(store.record(listOf(quota(2, 2))).isEmpty())
    }

    @Test fun `disconnect deletes baseline and history`() {
        val store = QuotaResetHistoryStore(fixture().context)
        store.record(listOf(quota(1)))
        store.record(listOf(quota(2, 1)))
        store.deleteService(AiService.CODEX)
        assertTrue(store.history.value.getValue(AiService.CODEX).isEmpty())
        assertTrue(store.record(listOf(quota(9, 2))).isEmpty())
    }

    @Test fun `failed persistence does not deliver notifications`() {
        val fixture = fixture()
        val store = QuotaResetHistoryStore(fixture.context)
        store.record(listOf(quota(1)))
        fixture.commitSucceeds = false
        assertTrue(store.record(listOf(quota(2, 1))).isEmpty())
        assertTrue(store.history.value.getValue(AiService.CODEX).isEmpty())
    }

    private fun quota(count: Int, seconds: Long = 0) = QuotaInfo(AiService.CODEX,
        emptyList(), null, fetchedAt = now.plusSeconds(seconds),
        codexResetCredits = CodexResetCredits(count, emptyList()))

    private class Fixture(val context: Context, var commitSucceeds: Boolean = true)

    private fun fixture(): Fixture {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        val saved = mutableMapOf<String, String>()
        val pending = mutableMapOf<String, String?>()
        val fixture = Fixture(context)
        `when`(context.getSharedPreferences(QuotaResetHistoryStore.PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getString(anyString(), isNull())).thenAnswer { saved[it.getArgument<String>(0)] }
        `when`(prefs.all).thenAnswer { saved.toMap() }
        `when`(prefs.edit()).thenAnswer { pending.clear(); editor }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            pending[it.getArgument(0)] = it.getArgument(1); editor
        }
        `when`(editor.remove(anyString())).thenAnswer { pending[it.getArgument(0)] = null; editor }
        `when`(editor.commit()).thenAnswer {
            if (fixture.commitSucceeds) pending.forEach { (key, value) ->
                if (value == null) saved.remove(key) else saved[key] = value
            }
            fixture.commitSucceeds
        }
        return fixture
    }
}
