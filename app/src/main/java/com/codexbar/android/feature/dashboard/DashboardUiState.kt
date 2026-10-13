package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.presentation.QuotaPresentationSnapshot

sealed class DashboardUiState {
    data object Loading : DashboardUiState()

    data class Content(
        val snapshot: QuotaPresentationSnapshot,
        val acquisitionMode: com.codexbar.android.core.domain.model.AcquisitionMode = com.codexbar.android.core.domain.model.AcquisitionMode.PHONE
    ) : DashboardUiState()
}
