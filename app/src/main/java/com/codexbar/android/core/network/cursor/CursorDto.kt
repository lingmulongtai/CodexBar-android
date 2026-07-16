package com.codexbar.android.core.network.cursor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object CursorDto {
    @Serializable
    data class UsageSummary(
        val billingCycleStart: String? = null,
        val billingCycleEnd: String? = null,
        val membershipType: String? = null,
        val limitType: String? = null,
        val isUnlimited: Boolean? = null,
        val individualUsage: IndividualUsage? = null,
        val teamUsage: TeamUsage? = null
    )

    @Serializable
    data class IndividualUsage(
        val plan: PlanUsage? = null,
        val onDemand: MeteredUsage? = null,
        val overall: MeteredUsage? = null
    )

    @Serializable
    data class TeamUsage(
        val onDemand: MeteredUsage? = null,
        val pooled: MeteredUsage? = null
    )

    @Serializable
    data class PlanUsage(
        val enabled: Boolean? = null,
        val used: Double? = null,
        val limit: Double? = null,
        val remaining: Double? = null,
        val breakdown: PlanBreakdown? = null,
        val autoPercentUsed: Double? = null,
        val apiPercentUsed: Double? = null,
        val totalPercentUsed: Double? = null
    )

    @Serializable
    data class PlanBreakdown(
        val included: Double? = null,
        val bonus: Double? = null,
        val total: Double? = null
    )

    @Serializable
    data class MeteredUsage(
        val enabled: Boolean? = null,
        val used: Double? = null,
        val limit: Double? = null,
        val remaining: Double? = null
    )

    @Serializable
    data class UserInfo(
        val email: String? = null,
        val name: String? = null,
        val sub: String? = null
    )

    @Serializable
    data class LegacyUsageResponse(
        @SerialName("gpt-4") val gpt4: LegacyModelUsage? = null,
        val startOfMonth: String? = null
    )

    @Serializable
    data class LegacyModelUsage(
        val numRequests: Int? = null,
        val numRequestsTotal: Int? = null,
        val numTokens: Long? = null,
        val maxRequestUsage: Int? = null,
        val maxTokenUsage: Long? = null
    )
}
