package com.codexbar.android.core.network.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object DeepSeekDto {
    @Serializable
    data class BalanceResponse(
        @SerialName("is_available") val isAvailable: Boolean,
        @SerialName("balance_infos") val balanceInfos: List<BalanceInfo>
    )

    @Serializable
    data class BalanceInfo(
        val currency: String,
        @SerialName("total_balance") val totalBalance: String,
        @SerialName("granted_balance") val grantedBalance: String,
        @SerialName("topped_up_balance") val toppedUpBalance: String
    )
}
