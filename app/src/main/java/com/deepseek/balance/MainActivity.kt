package com.deepseek.balance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deepseek.balance.data.model.BalanceUiState
import com.deepseek.balance.ui.screens.ApiKeyScreen
import com.deepseek.balance.ui.screens.DashboardScreen
import com.deepseek.balance.ui.theme.DeepSeekBalanceTheme
import com.deepseek.balance.viewmodel.BalanceViewModel
import com.deepseek.balance.worker.BalanceWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize notification channel
        BalanceWorker.createNotificationChannel(this)

        // Schedule background check every 30 minutes
        BalanceWorker.schedule(this, 30)

        setContent {
            DeepSeekBalanceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: BalanceViewModel = viewModel()
                    val hasApiKey by viewModel.hasApiKey.collectAsState()
                    val balanceState by viewModel.balanceState.collectAsState()
                    val isLoading by viewModel.isLoading.collectAsState()
                    val lastBalance by viewModel.lastBalance.collectAsState()
                    val usageState by viewModel.usageState.collectAsState()
                    val isUsageLoading by viewModel.isUsageLoading.collectAsState()
                    val lastUsage by viewModel.lastUsage.collectAsState()

                    // Animated transition between screens
                    AnimatedContent(
                        targetState = hasApiKey,
                        transitionSpec = {
                            if (targetState) {
                                slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                            }
                        },
                        label = "screen_transition"
                    ) { showDashboard ->
                        if (showDashboard) {
                            DashboardScreen(
                                balanceState = balanceState,
                                isLoading = isLoading,
                                lastBalance = lastBalance,
                                usageState = usageState,
                                isUsageLoading = isUsageLoading,
                                lastUsage = lastUsage,
                                onRefresh = { viewModel.refreshAll() },
                                onRefreshUsage = { viewModel.fetchUsage() },
                                onLogout = { viewModel.clearApiKey() }
                            )
                        } else {
                            ApiKeyScreen(
                                onSave = { apiKey -> viewModel.saveApiKey(apiKey) }
                            )
                        }
                    }
                }
            }
        }
    }
}
