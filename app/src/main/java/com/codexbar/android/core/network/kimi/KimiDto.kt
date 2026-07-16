package com.codexbar.android.core.network.kimi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

object KimiDto {
    @Serializable
    data class UsageEnvelope(
        val usage: UsageDetail,
        val limits: List<RateLimit>? = null
    )

    @Serializable
    data class UsageDetail(
        val limit: JsonPrimitive,
        val used: JsonPrimitive? = null,
        val remaining: JsonPrimitive? = null,
        val resetTime: String? = null,
        @SerialName("reset_time") val resetTimeSnake: String? = null,
        val resetAt: String? = null,
        @SerialName("reset_at") val resetAtSnake: String? = null
    ) {
        val resolvedResetTime: String?
            get() = sequenceOf(resetTime, resetAt, resetTimeSnake, resetAtSnake)
                .firstOrNull { !it.isNullOrBlank() }
    }

    @Serializable
    data class RateLimit(
        val window: Window,
        val detail: UsageDetail
    )

    @Serializable
    data class Window(
        val duration: JsonPrimitive,
        val timeUnit: String? = null,
        @SerialName("time_unit") val timeUnitSnake: String? = null
    ) {
        val resolvedTimeUnit: String?
            get() = timeUnit ?: timeUnitSnake
    }
}
