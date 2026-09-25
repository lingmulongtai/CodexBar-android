package com.codexbar.android.feature.dashboard

import android.content.Context
import com.codexbar.android.core.domain.model.AiService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardOrderPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("dashboard_display", Context.MODE_PRIVATE)

    fun load(): List<AiService> = DashboardProviderOrder.decode(prefs.getString("provider_order", null))

    fun save(visible: List<AiService>): List<AiService> {
        val order = if (visible.isEmpty()) emptyList() else DashboardProviderOrder.merge(load(), visible)
        prefs.edit().putString("provider_order", order.joinToString(",") { it.name }).apply()
        return order
    }
}
