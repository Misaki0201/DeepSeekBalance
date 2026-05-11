package com.deepseek.balance.data.model

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek API GET /v1/usage 响应（原始数据）
 */
data class UsageResponse(
    @SerializedName("data")
    val data: List<UsageRecord>
)

/**
 * 单次 API 请求的用量记录
 */
data class UsageRecord(
    @SerializedName("request_id")
    val requestId: String,

    @SerializedName("model")
    val model: String,

    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("completion_tokens")
    val completionTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int,

    @SerializedName("cost_in_cents")
    val costInCents: Double,

    @SerializedName("timestamp")
    val timestamp: String
)

/**
 * 聚合后的用量统计（UI 层使用）
 */
data class UsageStats(
    /** 今日 Token 消耗 */
    val todayTokens: Long = 0,
    /** 今日 Prompt Token 数 */
    val todayPromptTokens: Long = 0,
    /** 今日 Completion Token 数 */
    val todayCompletionTokens: Long = 0,
    /** 今日消费金额（元） */
    val todayCost: Double = 0.0,
    /** 累计 Token 消耗 */
    val totalTokens: Long = 0,
    /** 累计 Prompt Token 数 */
    val totalPromptTokens: Long = 0,
    /** 累计 Completion Token 数 */
    val totalCompletionTokens: Long = 0,
    /** 累计消费金额（元） */
    val totalCost: Double = 0.0,
    /** 数据最后更新时间 */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /** 将 cost_in_cents（分）转换为元 */
        private const val CENTS_PER_YUAN = 100.0

        fun fromRecords(records: List<UsageRecord>, todayDate: String): UsageStats {
            var todayTokens = 0L
            var todayPrompt = 0L
            var todayCompletion = 0L
            var todayCost = 0.0

            var totalTokens = 0L
            var totalPrompt = 0L
            var totalCompletion = 0L
            var totalCost = 0.0

            for (record in records) {
                totalTokens += record.totalTokens
                totalPrompt += record.promptTokens
                totalCompletion += record.completionTokens
                totalCost += record.costInCents / CENTS_PER_YUAN

                // 判断是否属于今日
                if (record.timestamp.startsWith(todayDate)) {
                    todayTokens += record.totalTokens
                    todayPrompt += record.promptTokens
                    todayCompletion += record.completionTokens
                    todayCost += record.costInCents / CENTS_PER_YUAN
                }
            }

            return UsageStats(
                todayTokens = todayTokens,
                todayPromptTokens = todayPrompt,
                todayCompletionTokens = todayCompletion,
                todayCost = todayCost,
                totalTokens = totalTokens,
                totalPromptTokens = totalPrompt,
                totalCompletionTokens = totalCompletion,
                totalCost = totalCost
            )
        }
    }
}

/**
 * 用量统计的 UI 状态
 */
sealed class UsageUiState {
    data object Loading : UsageUiState()
    data class Success(val stats: UsageStats) : UsageUiState()
    data class Error(val message: String) : UsageUiState()
}
