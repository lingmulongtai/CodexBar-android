package com.codexbar.android.core.network.zenmux

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ZenMuxDto {
    @Serializable
    data class SubscriptionEnvelope(
        val success: Boolean,
        val data: SubscriptionData
    )

    @Serializable
    data class SubscriptionData(
        val plan: Plan,
        @SerialName("account_status") val accountStatus: String,
        @SerialName("quota_5_hour") val fiveHour: Quota,
        @SerialName("quota_7_day") val sevenDay: Quota
    )

    @Serializable
    data class Plan(
        val tier: String,
        @SerialName("expires_at") val expiresAt: String? = null
    )

    @Serializable
    data class Quota(
        @SerialName("usage_percentage") val usagePercentage: Double,
        @SerialName("resets_at") val resetsAt: String? = null,
        @SerialName("max_flows") val maxFlows: Double,
        @SerialName("used_flows") val usedFlows: Double,
        @SerialName("remaining_flows") val remainingFlows: Double
    )
}
