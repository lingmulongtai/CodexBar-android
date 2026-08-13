package com.codexbar.android.core.network.ibmbob

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

object IbmBobDto {
    @Serializable
    data class ProfileResponse(
        val instances: List<Instance> = emptyList()
    )

    @Serializable
    data class Instance(
        @SerialName("instance_id") val instanceId: String,
        @SerialName("instance_name") val instanceName: String? = null,
        @SerialName("name") val legacyName: String? = null,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("plan_name") val planName: String? = null,
        @SerialName("refresh_at") val refreshAt: JsonPrimitive? = null,
        @SerialName("region_domain") val regionDomain: String? = null,
        val teams: List<Team> = emptyList()
    )

    @Serializable
    data class Team(
        val id: String,
        val name: String? = null,
        @SerialName("budget_limit") val budgetLimit: Double? = null,
        val usage: Double? = null
    )

    @Serializable
    data class TeamBudgetResponse(
        val usage: Double,
        @SerialName("budget_limit") val budgetLimit: Double? = null
    )
}
