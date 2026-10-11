package com.codexbar.android.core.data

import android.content.Context
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.QuotaResetActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaResetHistoryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _history = MutableStateFlow(loadHistory())
    val history = _history.asStateFlow()

    // Dashboard and WorkManager share this instance. Commit the baseline before
    // handing events to a notifier so overlapping refreshes cannot alert twice.
    @Synchronized
    fun record(quotas: List<QuotaInfo>): Map<AiService, List<QuotaResetActivity>> {
        val editor = prefs.edit()
        val activities = buildMap {
            for (quota in quotas) {
                val transition = QuotaResetTracker.observe(loadState(quota.service), quota)
                editor.putString(quota.service.name, json.encodeToString(transition.state))
                if (transition.activities.isNotEmpty()) put(quota.service, transition.activities)
            }
        }
        if (!editor.commit()) return emptyMap()
        _history.value = loadHistory()
        return activities
    }

    @Synchronized
    fun clearHistory(service: AiService) {
        // Keep the baseline and expiry deduplication when clearing the visible log.
        prefs.edit().putString(service.name, json.encodeToString(loadState(service).copy(history = emptyList()))).commit()
        _history.value = loadHistory()
    }

    @Synchronized
    fun deleteService(service: AiService) {
        prefs.edit().remove(service.name).commit()
        _history.value = loadHistory()
    }

    private fun loadState(service: AiService): QuotaResetState = runCatching {
        json.decodeFromString<QuotaResetState>(prefs.getString(service.name, null) ?: return QuotaResetState())
    }.getOrDefault(QuotaResetState())

    private fun loadHistory(): Map<AiService, List<QuotaResetActivity>> = AiService.entries.associateWith {
        loadState(it).history.filter { event ->
            event.observedAtEpochMillis >= System.currentTimeMillis() - QuotaResetTracker.RETENTION_MILLIS
        }.takeLast(QuotaResetTracker.MAX_HISTORY).asReversed()
    }

    internal companion object {
        const val PREFS_NAME = "codexbar_reset_history"
        const val BACKUP_PATH = "$PREFS_NAME.xml"
    }
}
