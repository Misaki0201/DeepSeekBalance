package com.deepseek.balance.data.repository

import com.deepseek.balance.data.api.DeepSeekApi
import com.deepseek.balance.data.model.BalanceDisplay
import com.deepseek.balance.data.model.BalanceResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BalanceRepository {

    private var api: DeepSeekApi? = null

    /**
     * 设置 API Key，初始化 API 客户端
     */
    fun setApiKey(apiKey: String) {
        api = DeepSeekApi.create(apiKey)
    }

    /**
     * 从 DeepSeek API 获取余额信息（直连模式）
     */
    suspend fun fetchBalance(): Result<BalanceDisplay> = withContext(Dispatchers.IO) {
        try {
            val currentApi = api
                ?: return@withContext Result.failure(Exception("API Key 未设置"))

            val response = currentApi.getBalance()
            val display = BalanceDisplay.fromResponse(response)
            Result.success(display)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("401") == true -> "API Key 无效，请检查后重试"
                e.message?.contains("403") == true -> "API Key 无权限访问"
                e.message?.contains("429") == true -> "请求过于频繁，请稍后重试"
                e.message?.contains("Timeout") == true -> "连接超时，请检查网络"
                else -> "获取余额失败: ${e.localizedMessage ?: "未知错误"}"
            }
            Result.failure(Exception(message))
        }
    }

    /**
     * 通过服务器代理获取余额
     */
    suspend fun fetchBalanceViaProxy(serverUrl: String): Result<BalanceDisplay> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.trimEnd('/')}/api/balance"
            val json = java.net.URL(url).readText()
            val response = Gson().fromJson(json, BalanceResponse::class.java)
            val display = BalanceDisplay.fromResponse(response)
            Result.success(display)
        } catch (e: Exception) {
            Result.failure(Exception("代理服务器请求失败: ${e.localizedMessage ?: "未知错误"}"))
        }
    }
}
