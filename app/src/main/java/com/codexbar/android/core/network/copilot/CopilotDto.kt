package com.codexbar.android.core.network.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

object CopilotDto {

    @Serializable
    data class UsageResponse(
        val quotaSnapshots: JsonObject? = null,
        @SerialName("quota_snapshots") val quotaSnapshotsSnake: JsonObject? = null,
        val copilotPlan: String? = null,
        @SerialName("copilot_plan") val copilotPlanSnake: String? = null
    ) {
        val snapshots: JsonObject?
            get() = quotaSnapshots ?: quotaSnapshotsSnake

        val plan: String?
            get() = copilotPlan ?: copilotPlanSnake
    }
}
