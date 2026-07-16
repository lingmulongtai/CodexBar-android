package com.codexbar.android.core.network.synthetic

import kotlinx.serialization.Serializable

object SyntheticDto {
    @Serializable
    data class QuotaEnvelope(
        val subscription: Subscription? = null
    )

    @Serializable
    data class Subscription(
        val limit: Double,
        val requests: Double,
        val renewsAt: String? = null
    )
}
