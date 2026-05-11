package com.deepseek.balance.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.balance.data.local.SettingsDataStore
import com.deepseek.balance.data.model.*
import com.deepseek.balance.data.repository.BalanceRepository
import com.deepseek.balance.data.repository.UsageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BalanceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val balanceRepository = BalanceRepository()
    private val usageRepository = UsageRepository()

    /** 余额历史快照（内存中） */
    private val balanceHistory = mutableListOf<BalanceSnapshot>()

    /** 是否已设置 API Key */
    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    /** 余额 UI 状态 */
    private val _balanceState = MutableStateFlow<BalanceUiState>(BalanceUiState.Loading)
    val balanceState: StateFlow<BalanceUiState> = _balanceState.asStateFlow()

    /** 最后成功获取的余额数据 */
    private val _lastBalance = MutableStateFlow<BalanceDisplay?>(null)
    val lastBalance: StateFlow<BalanceDisplay?> = _lastBalance.asStateFlow()

    /** 用量 UI 状态 */
    private val _usageState = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    val usageState: StateFlow<UsageUiState> = _usageState.asStateFlow()

    /** 最后成功获取的用量数据（缓存） */
    private val _lastUsage = MutableStateFlow<UsageStats?>(null)
    val lastUsage: StateFlow<UsageStats?> = _lastUsage.asStateFlow()

    /** 是否正在加载用量 */
    private val _isUsageLoading = MutableStateFlow(false)
    val isUsageLoading: StateFlow<Boolean> = _isUsageLoading.asStateFlow()

    /** 是否正在加载余额 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 是否使用服务器代理模式 */
    private val _useServerProxy = MutableStateFlow(false)
    val useServerProxy: StateFlow<Boolean> = _useServerProxy.asStateFlow()

    /** 代理服务器地址 */
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        // 监听 DataStore 中的 API Key
        viewModelScope.launch {
            settingsDataStore.apiKeyFlow.collect { apiKey ->
                if (!apiKey.isNullOrBlank()) {
                    balanceRepository.setApiKey(apiKey)
                    _hasApiKey.value = true
                    refreshBalance()
                    checkUsageAvailability()
                } else {
                    _hasApiKey.value = false
                    _balanceState.value = BalanceUiState.Loading
                    _usageState.value = UsageUiState.Loading
                }
            }
        }

        // 监听代理设置
        viewModelScope.launch {
            settingsDataStore.useServerProxyFlow.collect { useProxy ->
                _useServerProxy.value = useProxy
                if (useProxy) {
                    fetchUsage()
                }
            }
        }

        viewModelScope.launch {
            settingsDataStore.serverUrlFlow.collect { url ->
                _serverUrl.value = url ?: ""
            }
        }
    }

    /**
     * 保存 API Key
     */
    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsDataStore.saveApiKey(apiKey.trim())
        }
    }

    /**
     * 清除 API Key
     */
    fun clearApiKey() {
        viewModelScope.launch {
            settingsDataStore.clearApiKey()
            _hasApiKey.value = false
            _balanceState.value = BalanceUiState.Loading
            _usageState.value = UsageUiState.Loading
        }
    }

    /**
     * 设置服务器代理
     */
    fun setServerProxy(useProxy: Boolean, url: String = "") {
        viewModelScope.launch {
            settingsDataStore.setServerProxy(useProxy, url)
        }
    }

    /**
     * 刷新余额
     */
    fun refreshBalance() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _balanceState.value = BalanceUiState.Loading

            val result = if (_useServerProxy.value && _serverUrl.value.isNotBlank()) {
                balanceRepository.fetchBalanceViaProxy(_serverUrl.value)
            } else {
                balanceRepository.fetchBalance()
            }

            result.fold(
                onSuccess = { balance ->
                    // 记录余额快照
                    balanceHistory.add(BalanceSnapshot(balance.totalBalance, System.currentTimeMillis()))
                    // 只保留最近30天的记录
                    val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                    balanceHistory.removeAll { it.timestamp < thirtyDaysAgo }

                    _lastBalance.value = balance
                    _balanceState.value = BalanceUiState.Success(balance)

                    // 刷新用量统计
                    estimateUsage(balance)
                },
                onFailure = { error ->
                    val cached = _lastBalance.value
                    if (cached != null) {
                        _balanceState.value = BalanceUiState.Success(cached)
                    } else {
                        _balanceState.value = BalanceUiState.Error(error.message ?: "未知错误")
                    }
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 检查用量数据的可用性
     */
    private fun checkUsageAvailability() {
        if (_useServerProxy.value && _serverUrl.value.isNotBlank()) {
            fetchUsage()
        } else {
            val lastBalance = _lastBalance.value
            if (lastBalance != null) {
                estimateUsage(lastBalance)
            } else {
                _usageState.value = UsageUiState.RequiresServerProxy
            }
        }
    }

    /**
     * 从服务器代理获取用量统计
     */
    fun fetchUsage() {
        if (!_useServerProxy.value || _serverUrl.value.isBlank()) return
        if (_isUsageLoading.value) return

        viewModelScope.launch {
            _isUsageLoading.value = true
            _usageState.value = UsageUiState.Loading

            val result = usageRepository.fetchUsageFromServer(_serverUrl.value)

            result.fold(
                onSuccess = { stats ->
                    _lastUsage.value = stats
                    _usageState.value = UsageUiState.Success(stats)
                },
                onFailure = { error ->
                    _usageState.value = UsageUiState.Error(error.message ?: "无法连接服务器")
                }
            )

            _isUsageLoading.value = false
        }
    }

    /**
     * 基于余额变化估算用量
     */
    private fun estimateUsage(balance: BalanceDisplay) {
        viewModelScope.launch {
            val result = usageRepository.estimateUsage(
                currentBalance = balance.totalBalance,
                historySnapshots = balanceHistory.toList()
            )

            result.fold(
                onSuccess = { stats ->
                    val trend = usageRepository.getBalanceTrend(
                        snapshots = balanceHistory.toList(),
                        currentBalance = balance.totalBalance
                    )
                    _lastUsage.value = stats
                    _usageState.value = UsageUiState.Success(stats, trend)
                },
                onFailure = {
                    // 如果估算也失败，提示需要服务器代理
                    if (!_useServerProxy.value) {
                        _usageState.value = UsageUiState.RequiresServerProxy
                    } else {
                        _usageState.value = UsageUiState.Error(it.message ?: "获取用量失败")
                    }
                }
            )
        }
    }

    /**
     * 打开 DeepSeek 用量看板（外部浏览器）
     */
    fun openUsageDashboard(context: android.content.Context) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://platform.deepseek.com/usage")
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 刷新所有数据（余额 + 用量）
     */
    fun refreshAll() {
        refreshBalance()
        if (_useServerProxy.value && _serverUrl.value.isNotBlank()) {
            fetchUsage()
        }
    }
}
