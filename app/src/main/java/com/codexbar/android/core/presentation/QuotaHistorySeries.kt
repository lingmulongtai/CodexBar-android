package com.codexbar.android.core.presentation

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

object QuotaHistorySeries {
    fun compact(
        samples: List<QuotaHistorySample>,
        now: Instant
    ): List<QuotaHistorySample> {
        val oldestAllowed = now.minus(MAX_HISTORY_AGE)
        val recentCutoff = now.minus(RECENT_DETAIL_AGE)
        val normalized = samples
            .asSequence()
            .filter { sample ->
                sample.utilization.isFinite() &&
                    sample.fetchedAt >= oldestAllowed &&
                    sample.fetchedAt <= now.plusSeconds(MAX_FUTURE_SKEW_SECONDS)
            }
            .map { sample -> sample.copy(utilization = sample.utilization.coerceIn(0.0, 1.0)) }
            .distinctBy { sample ->
                Triple(sample.fetchedAt.toEpochMilli(), sample.resetsAt, sample.utilization)
            }
            .sortedBy(QuotaHistorySample::fetchedAt)
            .toList()

        val hourlyArchive = normalized
            .filter { it.fetchedAt < recentCutoff }
            .groupBy { it.fetchedAt.epochSecond / SECONDS_PER_HOUR }
            .values
            .map { hour -> hour.last() }
        val recent = normalized
            .filter { it.fetchedAt >= recentCutoff }
            .takeLast(MAX_RECENT_SAMPLES)

        return (hourlyArchive + recent)
            .sortedBy(QuotaHistorySample::fetchedAt)
            .takeLast(MAX_COMPACT_SAMPLES)
    }

    fun forChart(
        samples: List<QuotaHistorySample>,
        maxPoints: Int = DEFAULT_CHART_POINTS
    ): List<QuotaHistorySample> {
        if (samples.size <= maxPoints || maxPoints < 2) return samples

        val requiredIndices = linkedSetOf(0, samples.lastIndex)
        for (index in 1..samples.lastIndex) {
            val previous = samples[index - 1]
            val current = samples[index]
            if (previous.resetsAt != current.resetsAt || current.utilization < previous.utilization) {
                requiredIndices += index - 1
                requiredIndices += index
            }
        }
        if (requiredIndices.size >= maxPoints) {
            return requiredIndices.sorted().takeLast(maxPoints).map(samples::get)
        }

        val availableSlots = maxPoints - requiredIndices.size
        val candidates = samples.indices.filterNot(requiredIndices::contains)
        if (candidates.isNotEmpty()) {
            val stride = ceil(candidates.size.toDouble() / availableSlots.coerceAtLeast(1)).toInt()
            candidates.filterIndexed { index, _ -> index % stride == 0 }
                .take(availableSlots)
                .forEach(requiredIndices::add)
        }
        return requiredIndices.sorted().map(samples::get)
    }

    private val MAX_HISTORY_AGE: Duration = Duration.ofDays(90)
    private val RECENT_DETAIL_AGE: Duration = Duration.ofHours(24)
    private const val MAX_FUTURE_SKEW_SECONDS = 5 * 60L
    private const val SECONDS_PER_HOUR = 60L * 60L
    private const val MAX_RECENT_SAMPLES = 192
    private const val MAX_COMPACT_SAMPLES = 90 * 24 + MAX_RECENT_SAMPLES
    private const val DEFAULT_CHART_POINTS = 120
}
