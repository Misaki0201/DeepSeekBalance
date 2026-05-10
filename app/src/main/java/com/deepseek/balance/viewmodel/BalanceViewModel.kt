package com.deepseek.balance.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.balance.data.local.SettingsDataStore
import com.deepseek.balance.data.model.BalanceDisplay
import com.deepseek.balance.data.model.BalanceUiState
import com.deepseek.balance.data.repository.BalanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BalanceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val repository = BalanceRepository()

    /** 是否已设置 API Key */
    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    /** 余额 UI 状态 */
    private val _balanceState = MutableStateFlow<BalanceUiState>(BalanceUiState.Loading)
    val balanceState: StateFlow<BalanceUiState> = _balanceState.asStateFlow()

    /** 最后成功获取的余额数据 */
    private val _lastBalance = MutableStateFlow<BalanceDisplay?>(null)
    val lastBalance: StateFlow<BalanceDisplay?> = _lastBalance.asStateFlow()

    /** 是否正在加载 */
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
                    repository.setApiKey(apiKey)
                    _hasApiKey.value = true
                    refreshBalance()
                } else {
                    _hasApiKey.value = false
                    _balanceState.value = BalanceUiState.Loading
                }
            }
        }

        // 监听代理设置
        viewModelScope.launch {
            settingsDataStore.useServerProxyFlow.collect { useProxy ->
                _useServerProxy.value = useProxy
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
                repository.fetchBalanceViaProxy(_serverUrl.value)
            } else {
                repository.fetchBalance()
            }

            result.fold(
                onSuccess = { balance ->
                    _lastBalance.value = balance
                    _balanceState.value = BalanceUiState.Success(balance)
                },
                onFailure = { error ->
                    // 如果有缓存数据，降级显示
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
}
