package com.deepseek.balance.data.model

/**
 * 聚合后的用量统计（来自服务器代理追踪或余额变化估算）
 */
data class UsageStats(
    /** 今日 Token 消耗（估算或来自服务器追踪） */
    val todayTokens: Long = 0,
    /** 今日 Prompt Token 数 */
    val todayPromptTokens: Long = 0,
    /** 今日 Completion Token 数 */
    val todayCompletionTokens: Long = 0,
    /** 今日消费金额（元） */
    val todayCost: Double = 0.0,
    /** 累计 Token 消耗 */
    val totalTokens: Long = 0,
    /** 累计消费金额（元） */
    val totalCost: Double = 0.0,
    /** 数据来源：server=服务器追踪, estimate=余额估算, empty=无数据 */
    val source: String = "empty",
    /** 数据最后更新时间 */
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 余额历史快照（用于本地跟踪余额变化趋势）
 */
data class BalanceSnapshot(
    val balance: Double,
    val timestamp: Long
)

/**
 * 余额变化趋势
 */
data class BalanceTrend(
    /** 最近7天的余额快照 */
    val snapshots: List<BalanceSnapshot> = emptyList(),
    /** 今日余额变化（元） */
    val todayChange: Double = 0.0,
    /** 近7天日均消耗（元） */
    val dailyAvgCost: Double = 0.0
)

/**
 * 用量统计的 UI 状态
 */
sealed class UsageUiState {
    data object Loading : UsageUiState()
    data class Success(val stats: UsageStats, val trend: BalanceTrend = BalanceTrend()) : UsageUiState()
    data class Error(val message: String) : UsageUiState()
    data object RequiresServerProxy : UsageUiState()
}
