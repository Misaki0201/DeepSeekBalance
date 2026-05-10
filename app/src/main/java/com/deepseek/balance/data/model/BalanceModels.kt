package com.deepseek.balance.data.model

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek API GET /user/balance 响应
 */
data class BalanceResponse(
    @SerializedName("is_available")
    val isAvailable: Boolean,

    @SerializedName("balance_infos")
    val balanceInfos: List<BalanceInfo>
)

data class BalanceInfo(
    @SerializedName("currency")
    val currency: String,

    @SerializedName("total_balance")
    val totalBalance: String,

    @SerializedName("granted_balance")
    val grantedBalance: String,

    @SerializedName("topped_up_balance")
    val toppedUpBalance: String
)

/**
 * UI 层使用的余额状态
 */
sealed class BalanceUiState {
    data object Loading : BalanceUiState()
    data class Success(val balance: BalanceDisplay) : BalanceUiState()
    data class Error(val message: String) : BalanceUiState()
}

data class BalanceDisplay(
    val totalBalance: Double,
    val grantedBalance: Double,
    val toppedUpBalance: Double,
    val isAvailable: Boolean,
    val currency: String,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromResponse(response: BalanceResponse): BalanceDisplay {
            val info = response.balanceInfos.firstOrNull()
            return BalanceDisplay(
                totalBalance = info?.totalBalance?.toDoubleOrNull() ?: 0.0,
                grantedBalance = info?.grantedBalance?.toDoubleOrNull() ?: 0.0,
                toppedUpBalance = info?.toppedUpBalance?.toDoubleOrNull() ?: 0.0,
                isAvailable = response.isAvailable,
                currency = info?.currency ?: "CNY"
            )
        }
    }
}
