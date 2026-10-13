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
                if (!quota.historyEligible || quota.isStale) continue
                val legacy = archiveLegacy(quota.service, editor)
                val key = scopedKey(quota.service, quota.resetTrackingAccountKey)
                val previous = loadState(key).takeIf { prefs.getString(key, null) != null }
                    ?: loadState(quota.service).takeIf { it.accountKey == quota.resetTrackingAccountKey }
                    ?: QuotaResetState()
                val transition = QuotaResetTracker.observe(previous, quota)
                editor.putString(key, json.encodeToString(transition.state))
                editor.putString("active:${quota.service.name}", key)
                editor.putString(quota.service.name, json.encodeToString(transition.state))
                pruneScopes(quota.service, key, transition.state, editor, legacy)
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
        val state = loadState(service).copy(history = emptyList())
        val key = prefs.getString("active:${service.name}", null) ?: scopedKey(service, state.accountKey)
        prefs.edit().putString(service.name, json.encodeToString(state))
            .putString(key, json.encodeToString(state)).commit()
        _history.value = loadHistory()
    }

    @Synchronized
    fun deleteService(service: AiService) {
        val editor = prefs.edit().remove(service.name).remove("active:${service.name}")
        prefs.all.orEmpty().keys.filter { it.startsWith("scoped:${service.name}:") }.forEach(editor::remove)
        editor.commit()
        _history.value = loadHistory()
    }

    /** Hide the old source's log until its next observation, retaining scoped baselines. */
    @Synchronized
    fun deactivate(service: AiService) {
        val editor = prefs.edit()
        archiveLegacy(service, editor)
        editor.remove(service.name).remove("active:${service.name}").commit()
        _history.value = loadHistory()
    }

    private fun scopedKey(service: AiService, accountKey: String?) = "scoped:${service.name}:${historyScope(accountKey)}"
    private fun archiveLegacy(service: AiService, editor: android.content.SharedPreferences.Editor): Map<String, QuotaResetState> {
        if (prefs.getString(service.name, null) == null) return emptyMap()
        val state = loadState(service)
        val key = scopedKey(service, state.accountKey)
        if (prefs.getString(key, null) != null) return emptyMap()
        editor.putString(key, json.encodeToString(state))
        return mapOf(key to state)
    }
    private fun pruneScopes(service: AiService, currentKey: String, current: QuotaResetState, editor: android.content.SharedPreferences.Editor, legacy: Map<String, QuotaResetState>) {
        val prefix = "scoped:${service.name}:"
        val states = prefs.all.orEmpty().keys.filter { it.startsWith(prefix) }.associateWith(::loadState) + legacy + (currentKey to current)
        val cutoff = System.currentTimeMillis() - QuotaResetTracker.RETENTION_MILLIS
        val keep = states.entries.filter { it.value.fetchedAtEpochMillis >= cutoff }
            .sortedByDescending { it.value.fetchedAtEpochMillis }.take(8).map { it.key }.toSet()
        states.keys.filter { it !in keep }.forEach(editor::remove)
    }
    private fun loadState(service: AiService): QuotaResetState = loadState(service.name)
    private fun loadState(key: String): QuotaResetState = runCatching {
        json.decodeFromString<QuotaResetState>(prefs.getString(key, null) ?: return QuotaResetState())
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
