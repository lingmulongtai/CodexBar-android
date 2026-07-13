package com.codexbar.android.core.widget

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.QuotaMetricPresentation
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages per-widget configuration (selected services) and cached quota data.
 * Uses plain SharedPreferences (not encrypted) since widget data is non-sensitive display info.
 */
@Singleton
class WidgetPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("codexbar_widget_prefs", Context.MODE_PRIVATE)
    }

    // --- Per-widget service selection ---

    fun saveSelectedServices(appWidgetId: Int, services: Set<AiService>) {
        val key = "widget_${appWidgetId}_services"
        prefs.edit().putStringSet(key, services.map { it.name }.toSet()).commit()
    }

    fun getSelectedServices(appWidgetId: Int): Set<AiService> {
        val key = "widget_${appWidgetId}_services"
        val names = prefs.getStringSet(key, null) ?: return emptySet()
        return names.mapNotNull { name ->
            try { AiService.valueOf(name) } catch (_: Exception) { null }
        }.toSet()
    }

    fun deleteWidgetConfig(appWidgetId: Int) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("widget_${appWidgetId}_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    // --- Cached quota data for widgets ---

    fun cacheQuotaData(service: AiService, label: String, utilization: Double, resetsAtEpochSecond: Long?) {
        val prefix = "cache_${service.name}"
        prefs.edit()
            .putString("${prefix}_labels", getCachedLabels(service).plus(label).joinToString(","))
            .putFloat("${prefix}_${label}_util", utilization.toFloat())
            .apply {
                if (resetsAtEpochSecond != null) {
                    putLong("${prefix}_${label}_resets", resetsAtEpochSecond)
                } else {
                    remove("${prefix}_${label}_resets")
                }
            }
            .apply()
    }

    fun cacheAllQuotaData(service: AiService, windows: List<Triple<String, Double, Long?>>) {
        val prefix = "cache_${service.name}"
        val editor = prefs.edit()
        // Clear old cache for this service
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }

        val labels = windows.map { it.first }
        editor.putString("${prefix}_labels", labels.joinToString(","))
        for ((label, utilization, resetsAt) in windows) {
            editor.putFloat("${prefix}_${label}_util", utilization.toFloat())
            if (resetsAt != null) {
                editor.putLong("${prefix}_${label}_resets", resetsAt)
            }
        }
        editor.putLong("${prefix}_updated_at", System.currentTimeMillis())
        editor.apply()
    }

    fun cachePresentation(service: ServiceQuotaPresentation) {
        val prefix = "cache_${service.service.name}"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }

        val labels = service.metrics.map { it.label }
        editor.putString("${prefix}_labels", labels.joinToString(","))
        editor.putLong("${prefix}_updated_at", System.currentTimeMillis())
        editor.putString("${prefix}_status", service.status.name)
        editor.putString("${prefix}_freshness", service.freshness.ageLabel)

        service.tier?.let { editor.putString("${prefix}_tier", it) }

        for (metric in service.metrics) {
            editor.cacheMetric(prefix, metric)
        }
        editor.apply()
    }

    private fun SharedPreferences.Editor.cacheMetric(prefix: String, metric: QuotaMetricPresentation) {
        val label = metric.label
        putFloat("${prefix}_${label}_util", (metric.usedFraction ?: 0.0).toFloat())
        putFloat("${prefix}_${label}_bar", metric.barProgress)
        putString("${prefix}_${label}_remaining_label", metric.remainingLabel)
        putString("${prefix}_${label}_used_label", metric.usedLabel)
        putString("${prefix}_${label}_reset_label", metric.resetLabel)
        putString("${prefix}_${label}_pace_label", metric.pace.label)
        putString("${prefix}_${label}_severity", metric.severity.name)
        metric.resetsAt?.let { putLong("${prefix}_${label}_resets", it.epochSecond) }
    }

    fun getCachedLabels(service: AiService): List<String> {
        val raw = prefs.getString("cache_${service.name}_labels", null) ?: return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }
    }

    fun getCachedUtilization(service: AiService, label: String): Float {
        return prefs.getFloat("cache_${service.name}_${label}_util", 0f)
    }

    fun getCachedBarProgress(service: AiService, label: String): Float {
        return prefs.getFloat("cache_${service.name}_${label}_bar", (1f - getCachedUtilization(service, label)).coerceIn(0f, 1f))
    }

    fun getCachedRemainingLabel(service: AiService, label: String): String {
        return prefs.getString("cache_${service.name}_${label}_remaining_label", null)
            ?: "${((1f - getCachedUtilization(service, label)) * 100).toInt().coerceIn(0, 100)}% left"
    }

    fun getCachedResetLabel(service: AiService, label: String): String? {
        return prefs.getString("cache_${service.name}_${label}_reset_label", null)
    }

    fun getCachedPaceLabel(service: AiService, label: String): String? {
        return prefs.getString("cache_${service.name}_${label}_pace_label", null)
    }

    fun getCachedSeverity(service: AiService, label: String): String? {
        return prefs.getString("cache_${service.name}_${label}_severity", null)
    }

    fun getCachedFreshness(service: AiService): String? {
        return prefs.getString("cache_${service.name}_freshness", null)
    }

    fun getCachedResetsAt(service: AiService, label: String): Long? {
        val value = prefs.getLong("cache_${service.name}_${label}_resets", -1L)
        return if (value > 0) value else null
    }

    fun getCachedUpdatedAt(service: AiService): Long {
        return prefs.getLong("cache_${service.name}_updated_at", 0L)
    }

    /** Returns the highest utilization across all cached windows for this service. */
    fun getMaxCachedUtilization(service: AiService): Float {
        val labels = getCachedLabels(service)
        if (labels.isEmpty()) return 0f
        return labels.maxOf { getCachedUtilization(service, it) }
    }

    fun cacheTier(service: AiService, tier: String?) {
        val key = "cache_${service.name}_tier"
        if (tier != null) {
            prefs.edit().putString(key, tier).apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }

    fun getCachedTier(service: AiService): String? {
        return prefs.getString("cache_${service.name}_tier", null)
    }
}
