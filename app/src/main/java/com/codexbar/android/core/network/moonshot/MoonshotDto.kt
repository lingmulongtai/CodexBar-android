package com.codexbar.android.core.network.moonshot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object MoonshotDto {
    @Serializable
    data class BalanceResponse(
        val status: Boolean,
        val scode: String? = null,
        val data: BalanceData
    )

    @Serializable
    data class BalanceData(
        @SerialName("available_balance") val availableBalance: Double,
        @SerialName("voucher_balance") val voucherBalance: Double,
        @SerialName("cash_balance") val cashBalance: Double
    )
}
