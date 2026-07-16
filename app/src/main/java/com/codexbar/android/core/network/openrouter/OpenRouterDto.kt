package com.codexbar.android.core.network.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object OpenRouterDto {
    @Serializable
    data class KeyEnvelope(
        val data: KeyData
    )

    @Serializable
    data class KeyData(
        val limit: Double? = null,
        @SerialName("limit_remaining") val limitRemaining: Double? = null,
        @SerialName("limit_reset") val limitReset: String? = null,
        val usage: Double = 0.0,
        @SerialName("usage_daily") val usageDaily: Double = 0.0,
        @SerialName("usage_weekly") val usageWeekly: Double = 0.0,
        @SerialName("usage_monthly") val usageMonthly: Double = 0.0,
        @SerialName("is_free_tier") val isFreeTier: Boolean = false,
        @SerialName("expires_at") val expiresAt: String? = null
    )
}
