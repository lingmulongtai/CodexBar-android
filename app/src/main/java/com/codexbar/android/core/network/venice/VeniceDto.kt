package com.codexbar.android.core.network.venice

import kotlinx.serialization.Serializable

object VeniceDto {
    @Serializable
    data class BalanceResponse(
        val canConsume: Boolean,
        val consumptionCurrency: String? = null,
        val balances: Balances,
        val diemEpochAllocation: Double
    )

    @Serializable
    data class Balances(
        val diem: Double? = null,
        val usd: Double? = null
    )
}
