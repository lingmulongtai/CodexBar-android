package com.codexbar.android.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class QuotaResetKind { WINDOW_RESET, CREDITS_ADDED, CREDITS_DECREASED, CREDITS_EXPIRING }

@Serializable
data class QuotaResetActivity(
    val kind: QuotaResetKind,
    val observedAtEpochMillis: Long,
    val windowLabel: String? = null,
    val count: Int = 0,
    val availableCount: Int? = null,
    val boundaryEpochSeconds: Long? = null
)
