package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.QuotaResetActivity
import com.codexbar.android.core.domain.model.QuotaResetKind
import kotlinx.serialization.Serializable

@Serializable
internal data class QuotaResetState(
    val accountKey: String? = null,
    val fetchedAtEpochMillis: Long = 0,
    val resetTimes: Map<String, Long> = emptyMap(),
    val availableCredits: Int? = null,
    val warnedExpiries: Set<Long> = emptySet(),
    val history: List<QuotaResetActivity> = emptyList()
)

internal data class QuotaResetTransition(
    val state: QuotaResetState,
    val activities: List<QuotaResetActivity>
)

internal object QuotaResetTracker {
    const val MAX_HISTORY = 100
    const val RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
    private const val EXPIRY_WARNING_SECONDS = 24L * 60 * 60

    fun observe(stored: QuotaResetState, quota: QuotaInfo): QuotaResetTransition {
        val previous = if (stored.accountKey == quota.resetTrackingAccountKey) stored else QuotaResetState()
        val observedAt = quota.fetchedAt.toEpochMilli()
        if (observedAt <= previous.fetchedAtEpochMillis) {
            return QuotaResetTransition(previous, emptyList())
        }
        val now = quota.fetchedAt.epochSecond
        val resetTimes = quota.windows.take(32).mapNotNull { window ->
            window.resetsAt?.epochSecond?.takeIf { it > 0 }?.let { window.label.take(128) to it }
        }.toMap()
        val credits = quota.codexResetCredits?.takeIf { it.availableCount >= 0 }
        val expiries = credits?.expiresAt.orEmpty().map { it.epochSecond }.filter { it > now }.sorted().take(32).toSet()
        val activities = buildList {
            for ((label, resetAt) in resetTimes) {
                val boundary = previous.resetTimes[label] ?: continue
                if (boundary <= now && resetAt > now && resetAt > boundary) {
                    add(QuotaResetActivity(QuotaResetKind.WINDOW_RESET, observedAt,
                        windowLabel = label, boundaryEpochSeconds = boundary))
                }
            }
            if (credits != null && previous.availableCredits != null) {
                val delta = credits.availableCount - previous.availableCredits
                if (delta != 0) {
                    add(QuotaResetActivity(
                        kind = if (delta > 0) QuotaResetKind.CREDITS_ADDED else QuotaResetKind.CREDITS_DECREASED,
                        observedAtEpochMillis = observedAt,
                        count = kotlin.math.abs(delta),
                        availableCount = credits.availableCount
                    ))
                }
                for (expiry in expiries.sorted().takeIf { credits.availableCount > 0 }.orEmpty()) {
                    if (expiry - now <= EXPIRY_WARNING_SECONDS && expiry !in previous.warnedExpiries) {
                        add(QuotaResetActivity(QuotaResetKind.CREDITS_EXPIRING, observedAt,
                            boundaryEpochSeconds = expiry))
                    }
                }
            }
        }
        val history = (previous.history + activities)
            .filter { it.observedAtEpochMillis >= observedAt - RETENTION_MILLIS }
            .takeLast(MAX_HISTORY)
        return QuotaResetTransition(
            QuotaResetState(
                accountKey = quota.resetTrackingAccountKey,
                fetchedAtEpochMillis = observedAt,
                resetTimes = (previous.resetTimes + resetTimes).entries.takeLastEntries(32),
                availableCredits = credits?.availableCount ?: previous.availableCredits,
                warnedExpiries = if (credits == null) previous.warnedExpiries else
                    (previous.warnedExpiries.intersect(expiries) + activities
                        .filter { it.kind == QuotaResetKind.CREDITS_EXPIRING }
                        .mapNotNull { it.boundaryEpochSeconds }),
                history = history
            ),
            activities
        )
    }

    private fun Set<Map.Entry<String, Long>>.takeLastEntries(count: Int): Map<String, Long> =
        toList().takeLast(count).associate { it.key to it.value }
}
