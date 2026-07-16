package com.codexbar.android.core.network.zai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ZaiDto {
    @Serializable
    data class QuotaEnvelope(
        val code: Int,
        val msg: String? = null,
        val data: QuotaData? = null,
        val success: Boolean
    )

    @Serializable
    data class QuotaData(
        val limits: List<Limit> = emptyList(),
        val planName: String? = null,
        val plan: String? = null,
        @SerialName("plan_type") val planType: String? = null,
        val packageName: String? = null
    ) {
        val resolvedPlanName: String?
            get() = sequenceOf(planName, plan, planType, packageName)
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()
    }

    @Serializable
    data class Limit(
        val type: String,
        val unit: Int = 0,
        val number: Int = 0,
        val usage: Long? = null,
        val currentValue: Long? = null,
        val remaining: Long? = null,
        val percentage: Double = 0.0,
        val nextResetTime: Long? = null
    )
}
