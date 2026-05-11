package com.deepseek.balance.data.repository

import com.deepseek.balance.data.api.DeepSeekApi
import com.deepseek.balance.data.model.UsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class UsageRepository {

    private var api: DeepSeekApi? = null

    fun setApiKey(apiKey: String) {
        api = DeepSeekApi.create(apiKey)
    }

    /**
     * 获取用量统计
     * 查询今日数据 + 历史累计数据
     */
    suspend fun fetchUsageStats(): Result<UsageStats> = withContext(Dispatchers.IO) {
        try {
            val currentApi = api
                ?: return@withContext Result.failure(Exception("API Key 未设置"))

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())

            // 查询较长时间范围的用量数据（从 2025-01-01 到今日）
            // DeepSeek 会返回该时间范围内的所有请求记录
            val response = currentApi.getUsage(
                startDate = "2025-01-01",
                endDate = today
            )

            val stats = UsageStats.fromRecords(response.data, today)
            Result.success(stats)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("401") == true -> "API Key 无权限查看用量"
                e.message?.contains("403") == true -> "API Key 无权限访问用量数据"
                e.message?.contains("429") == true -> "请求过于频繁，请稍后重试"
                e.message?.contains("Timeout") == true -> "连接超时，请检查网络"
                else -> "获取用量失败: ${e.localizedMessage ?: "未知错误"}"
            }
            Result.failure(Exception(message))
        }
    }
}
