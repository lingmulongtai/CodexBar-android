package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.AiService

/** Display order is independent of the usage-based notification priority. */
internal object DashboardProviderOrder {
    fun decode(value: String?): List<AiService> = value.orEmpty().split(',')
        .mapNotNull { name -> AiService.entries.firstOrNull { it.name == name } }.distinct()

    fun <T> apply(items: List<T>, order: List<AiService>, service: (T) -> AiService): List<T> {
        val ranks = order.withIndex().associate { it.value to it.index }
        return items.sortedBy { ranks[service(it)] ?: Int.MAX_VALUE }
    }

    /** Keep disconnected providers in their saved slots until they reconnect. */
    fun merge(saved: List<AiService>, visible: List<AiService>): List<AiService> {
        val remaining = visible.distinct().toMutableList()
        val merged = saved.distinct().map { service ->
            if (service in visible) remaining.removeAt(0) else service
        }
        return merged + remaining
    }
}
