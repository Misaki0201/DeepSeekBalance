package com.deepseek.balance.data.repository

import com.deepseek.balance.data.model.BalanceSnapshot
import com.deepseek.balance.data.model.BalanceTrend
import com.deepseek.balance.data.model.UsageStats
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class UsageRepository {

    companion object {
        private const val HISTORY_FILE = "balance_history.json"
        private val gson = Gson()
    }

    /**
     * 通过服务器代理获取用量统计（最准确的方式）
     */
    suspend fun fetchUsageFromServer(serverUrl: String): Result<UsageStats> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${serverUrl.trimEnd('/')}/api/usage-stats")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return@withContext Result.failure(
                    Exception("服务器返回错误: HTTP $responseCode")
                )
            }

            val json = connection.inputStream.bufferedReader().readText()
            val response = gson.fromJson(json, ServerUsageResponse::class.java)
            connection.disconnect()

            val stats = UsageStats(
                todayTokens = response.todayTokens,
                todayPromptTokens = response.todayPrompt,
                todayCompletionTokens = response.todayCompletion,
                todayCost = response.todayCost,
                totalTokens = response.totalTokens,
                totalCost = response.totalCost,
                source = "server",
                lastUpdated = System.currentTimeMillis()
            )
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(Exception("无法连接服务器: ${e.localizedMessage ?: "未知错误"}"))
        }
    }

    /**
     * 估算用量（基于余额变化和充值历史）
     * 当没有服务器代理时，用此方法提供粗略估算
     */
    suspend fun estimateUsage(
        currentBalance: Double,
        historySnapshots: List<BalanceSnapshot>
    ): Result<UsageStats> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val todayStart = getTodayStartMillis()

            // 计算今日余额变化
            val todaySnapshots = historySnapshots.filter { it.timestamp >= todayStart }
            val currentSnapshot = BalanceSnapshot(currentBalance, now)
            val allTodaySnapshots = todaySnapshots + currentSnapshot

            val todayCost = if (allTodaySnapshots.size >= 2) {
                // 今日最大余额 - 当前余额 = 今日消耗
                val maxTodayBalance = allTodaySnapshots.maxOf { it.balance }
                val minTodayBalance = allTodaySnapshots.minOf { it.balance }
                val diff = maxTodayBalance - minTodayBalance
                if (diff > 0) diff else 0.0
            } else 0.0

            // 近7天日均消耗
            val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
            val weekSnapshots = historySnapshots.filter { it.timestamp >= weekAgo } + currentSnapshot
            val dailyAvgCost = if (weekSnapshots.size >= 2) {
                val maxWeek = weekSnapshots.maxOf { it.balance }
                val minWeek = weekSnapshots.minOf { it.balance }
                val weekDiff = maxWeek - minWeek
                if (weekDiff > 0) weekDiff / 7.0 else 0.0
            } else 0.0

            // 粗略估算 Token 数（按平均价格 ¥6/百万 tokens 估算）
            val totalTokensEstimate = (todayCost / 6.0 * 1_000_000).toLong()
            val totalCostEstimate = historySnapshots.size * dailyAvgCost // 粗略累计

            val stats = UsageStats(
                todayTokens = totalTokensEstimate,
                todayCost = todayCost,
                totalTokens = totalTokensEstimate * historySnapshots.size.coerceAtLeast(1),
                totalCost = totalCostEstimate,
                source = "estimate",
                lastUpdated = now
            )
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(Exception("估算用量失败: ${e.localizedMessage ?: "未知错误"}"))
        }
    }

    /**
     * 获取余额趋势数据
     */
    fun getBalanceTrend(
        snapshots: List<BalanceSnapshot>,
        currentBalance: Double
    ): BalanceTrend {
        val now = System.currentTimeMillis()
        val currentSnapshot = BalanceSnapshot(currentBalance, now)
        val allSnapshots = snapshots + currentSnapshot

        // 近7天
        val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
        val weekSnapshots = allSnapshots.filter { it.timestamp >= weekAgo }

        // 今日常用变化
        val todayStart = getTodayStartMillis()
        val todaySnapshots = allSnapshots.filter { it.timestamp >= todayStart }
        val todayChange = if (todaySnapshots.size >= 2) {
            todaySnapshots.last().balance - todaySnapshots.first().balance
        } else 0.0

        // 日均
        val dailyAvgCost = if (weekSnapshots.size >= 2) {
            val diff = weekSnapshots.first().balance - weekSnapshots.last().balance
            if (diff > 0) diff / 7.0 else 0.0
        } else 0.0

        return BalanceTrend(
            snapshots = weekSnapshots,
            todayChange = todayChange,
            dailyAvgCost = dailyAvgCost
        )
    }

    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 服务器用量统计响应体
     */
    data class ServerUsageResponse(
        val todayTokens: Long = 0,
        val todayPrompt: Long = 0,
        val todayCompletion: Long = 0,
        val todayCost: Double = 0.0,
        val totalTokens: Long = 0,
        val totalCost: Double = 0.0
    )
}
