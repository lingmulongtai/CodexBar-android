package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.presentation.QuotaPresentationSnapshot

sealed class DashboardUiState {
    data object Loading : DashboardUiState()

    data class Content(
        val snapshot: QuotaPresentationSnapshot,
        val acquisitionMode: com.codexbar.android.core.domain.model.AcquisitionMode = com.codexbar.android.core.domain.model.AcquisitionMode.PHONE
    ) : DashboardUiState()
}

/**
 * Progress of the refresh in flight. The dashboard keeps showing the last readings while each
 * provider in [pending] is fetched, so a refresh never blocks reading or navigating.
 */
data class DashboardRefreshState(
    val active: Boolean = false,
    val total: Int = 0,
    val pending: Set<com.codexbar.android.core.domain.model.AiService> = emptySet()
) {
    val completed: Int get() = (total - pending.size).coerceAtLeast(0)
}
