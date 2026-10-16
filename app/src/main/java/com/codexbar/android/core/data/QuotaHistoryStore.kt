package com.codexbar.android.core.data

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.presentation.AndroidQuotaPresentationText
import com.codexbar.android.core.presentation.PacePresentation
import com.codexbar.android.core.presentation.QuotaHistorySample
import com.codexbar.android.core.presentation.QuotaHistorySeries
import com.codexbar.android.core.presentation.QuotaPaceCalculator
import com.codexbar.android.core.presentation.QuotaPresentationMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaHistoryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val calculator = QuotaPaceCalculator(AndroidQuotaPresentationText(context))

    @Synchronized fun record(quotas: List<QuotaInfo>) {
        val editor = prefs.edit()
        val written = mutableSetOf<String>()
        for (quota in quotas) {
            if ((!quota.historyEligible && quota.desktopHistoryScopeKey == null) || quota.isStale) continue
            if (quota.isDesktopSource && quota.desktopHistoryScopeKey != null) {
                val observedKey = "${quota.service.name}:scoped:${historyScope(quota.desktopHistoryScopeKey)}:observed"
                val previous = prefs.getString(observedKey, null)?.toLongOrNull() ?: 0L
                if (quota.fetchedAt.toEpochMilli() <= previous) continue
                editor.putString(observedKey, quota.fetchedAt.toEpochMilli().toString())
                written += observedKey
                // Replace from the PC's canonical series; never append a phone's polling times.
                for (window in quota.windows) {
                    val samples = quota.desktopQuotaHistory.firstOrNull { it.label == window.label }?.samples.orEmpty()
                        .map { QuotaHistorySample(it.at, it.utilization, it.resetsAt) }
                    val key = historyKey(quota, window.label)
                    editor.putString(key, samples.joinToString("\n") { it.serialize() })
                    written += key
                }
                pruneScopes(quota, editor, written)
                continue
            }
            val scopeKey = "scope:${quota.service.name}"
            val scope = quota.resetTrackingAccountKey
            val legacyMatches = prefs.getString(scopeKey, null) == scope
            // Move a matching pre-v0.9.7 series once; never assign it to another account.
            val legacyPrefix = "${quota.service.name}:"
            val scopedPrefix = "${quota.service.name}:scoped:${historyScope(scope)}:"
            val legacy = prefs.all.keys.filter { it.startsWith(legacyPrefix) && !it.startsWith("${quota.service.name}:scoped:") }
            if (legacyMatches) {
                legacy.forEach { key ->
                    val target = scopedPrefix + key.removePrefix(legacyPrefix)
                    if (!prefs.contains(target)) editor.putString(target, prefs.getString(key, null))
                    editor.remove(key)
                }
                editor.remove(scopeKey)
            }
            for (window in quota.windows) {
                val key = historyKey(quota, window.label)
                val sample = QuotaHistorySample(
                    fetchedAt = quota.fetchedAt,
                    utilization = window.utilization.coerceIn(0.0, 1.0),
                    resetsAt = window.resetsAt
                )
                val samples = QuotaHistorySeries.compact(
                    samples = (if (prefs.getString(key, null) == null && legacyMatches)
                        loadSamples(legacyPrefix + URLEncoder.encode(window.label, StandardCharsets.UTF_8.name()))
                        else loadSamples(key)) + sample,
                    now = sample.fetchedAt
                )
                editor.putString(key, samples.joinToString("\n") { it.serialize() })
                written += key
            }
            pruneScopes(quota, editor, written)
        }
        editor.apply()
    }

    fun paceFor(quotas: List<QuotaInfo>, now: Instant): Map<String, PacePresentation> {
        return buildMap {
            for (quota in quotas) {
                if ((!quota.historyEligible && quota.desktopHistoryScopeKey == null) || quota.isStale) continue
                for (window in quota.windows) {
                    put(
                        QuotaPresentationMapper.metricKey(quota.service, window.label),
                        calculator.calculate(
                            samples = loadSamples(historyKey(quota, window.label)),
                            currentWindow = window,
                            now = now
                        )
                    )
                }
            }
        }
    }

    fun historyFor(quotas: List<QuotaInfo>): Map<String, List<QuotaHistorySample>> {
        return buildMap {
            for (quota in quotas) {
                if ((!quota.historyEligible && quota.desktopHistoryScopeKey == null) || quota.isStale) continue
                for (window in quota.windows) {
                    put(
                        QuotaPresentationMapper.metricKey(quota.service, window.label),
                        QuotaHistorySeries.forChart(
                            loadSamples(historyKey(quota, window.label))
                        )
                    )
                }
            }
        }
    }

    @Synchronized fun deleteService(service: AiService) {
        val prefix = "${service.name}:"
        val editor = prefs.edit()
        editor.remove("scope:${service.name}")
        editor.remove("scopes:${service.name}")
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun loadSamples(key: String): List<QuotaHistorySample> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return QuotaHistorySeries.compact(raw.lineSequence()
            .mapNotNull { line -> line.deserializeSampleOrNull() }
            .toList(), Instant.now())
    }

    private fun pruneScopes(quota: QuotaInfo, editor: SharedPreferences.Editor, written: Set<String>) {
        val now = System.currentTimeMillis()
        val indexKey = "scopes:${quota.service.name}"
        val times = prefs.getString(indexKey, null).orEmpty().lineSequence().mapNotNull { line ->
            val fields = line.split(':')
            fields.getOrNull(1)?.toLongOrNull()?.let { fields[0] to it }
        }.toMap() + (historyScope(quota.desktopHistoryScopeKey ?: quota.resetTrackingAccountKey) to now)
        val retained = times.entries.filter { it.value >= now - QuotaResetTracker.RETENTION_MILLIS }
            .sortedByDescending { it.value }.take(8).associate { it.key to it.value }
        editor.putString(indexKey, retained.entries.joinToString("\n") { "${it.key}:${it.value}" })
        val prefix = "${quota.service.name}:scoped:"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
            if (key in written) return@forEach
            if (key.removePrefix(prefix).substringBefore(':') !in retained) editor.remove(key)
            else {
                if (key.endsWith(":observed")) return@forEach
                val recent = loadSamples(key)
                if (recent.isEmpty()) editor.remove(key)
                else if (!key.startsWith(prefix + historyScope(quota.desktopHistoryScopeKey ?: quota.resetTrackingAccountKey) + ":"))
                    editor.putString(key, recent.joinToString("\n") { it.serialize() })
            }
        }
    }

    private fun historyKey(quota: QuotaInfo, label: String): String {
        return "${quota.service.name}:scoped:${historyScope(quota.desktopHistoryScopeKey ?: quota.resetTrackingAccountKey)}:${URLEncoder.encode(label, StandardCharsets.UTF_8.name())}"
    }

    private fun QuotaHistorySample.serialize(): String {
        return listOf(
            fetchedAt.toEpochMilli().toString(),
            utilization.toString(),
            resetsAt?.epochSecond?.toString().orEmpty()
        ).joinToString("|")
    }

    private fun String.deserializeSampleOrNull(): QuotaHistorySample? {
        val parts = split("|")
        if (parts.size < 2) return null
        return runCatching {
            QuotaHistorySample(
                fetchedAt = Instant.ofEpochMilli(parts[0].toLong()),
                utilization = parts[1].toDouble(),
                resetsAt = parts.getOrNull(2)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Instant.ofEpochSecond(it.toLong()) }
            )
        }.getOrNull()
    }

    internal companion object {
        const val PREFS_NAME = "codexbar_quota_history"
        const val BACKUP_PATH = "$PREFS_NAME.xml"
    }
}

/** Hash account keys in preference names; null is the legacy on-phone source. */
internal fun historyScope(accountKey: String?): String = MessageDigest.getInstance("SHA-256")
    .digest((accountKey ?: "phone").toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
