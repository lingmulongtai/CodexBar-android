package com.codexbar.android.feature.dashboard

import android.content.Context
import com.codexbar.android.core.domain.model.AiService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ProviderOrderScope(internal val key: String) {
    USAGE("provider_order"), TRENDS("trends_provider_order"), CONNECTIONS("connections_provider_order")
}

@Singleton
class DashboardOrderPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("dashboard_display", Context.MODE_PRIVATE)

    fun load(scope: ProviderOrderScope = ProviderOrderScope.USAGE): List<AiService> =
        DashboardProviderOrder.decode(prefs.getString(scope.key, null))

    fun save(visible: List<AiService>, scope: ProviderOrderScope = ProviderOrderScope.USAGE): List<AiService> {
        val order = if (visible.isEmpty()) emptyList() else DashboardProviderOrder.merge(load(scope), visible)
        prefs.edit().putString(scope.key, order.joinToString(",") { it.name }).apply()
        return order
    }
}
