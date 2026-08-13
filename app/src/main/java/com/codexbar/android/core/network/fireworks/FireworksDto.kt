package com.codexbar.android.core.network.fireworks

import kotlinx.serialization.Serializable

object FireworksDto {
    @Serializable
    data class BillingSummaryResponse(
        val lineItems: List<LineItem>? = null
    )

    @Serializable
    data class LineItem(
        val totalCost: Money? = null
    )

    @Serializable
    data class Money(
        val currencyCode: String? = null,
        val nanos: Long? = null,
        val units: String? = null
    )
}
