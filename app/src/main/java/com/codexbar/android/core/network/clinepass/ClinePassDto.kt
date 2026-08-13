package com.codexbar.android.core.network.clinepass

import kotlinx.serialization.Serializable

object ClinePassDto {
    @Serializable
    data class UsageLimitsEnvelope(
        val success: Boolean,
        val data: UsageLimitsData? = null
    )

    @Serializable
    data class UsageLimitsData(
        val limits: List<UsageLimit> = emptyList()
    )

    @Serializable
    data class UsageLimit(
        val type: String,
        val percentUsed: Double,
        val resetsAt: String? = null
    )
}
