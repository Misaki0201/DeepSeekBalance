package com.deepseek.balance.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.deepseek.balance.MainActivity
import com.deepseek.balance.data.api.DeepSeekApi
import com.deepseek.balance.data.local.SettingsDataStore
import com.deepseek.balance.data.model.BalanceDisplay
import com.deepseek.balance.data.model.BalanceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class BalanceWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "deepseek_balance_channel"
        const val NOTIFICATION_ID = 1001
        const val LOW_BALANCE_NOTIFICATION_ID = 1002
        const val WORK_NAME = "balance_check_work"

        private const val LOW_BALANCE_THRESHOLD = 10.0

        fun schedule(context: Context, intervalMinutes: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BalanceWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DeepSeek 余额",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "DeepSeek API 余额变动通知"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settingsDataStore = SettingsDataStore(applicationContext)

            // Collect the current API key
            var apiKey: String? = null
            val job = kotlinx.coroutines.launch {
                settingsDataStore.apiKeyFlow.collect { key ->
                    apiKey = key
                }
            }
            // Wait for the value
            kotlinx.coroutines.delay(500)
            job.cancel()

            if (apiKey.isNullOrBlank()) {
                return@withContext Result.success()
            }

            // Fetch balance
            val deepSeekApi = DeepSeekApi.create(apiKey!!)
            val response = deepSeekApi.getBalance()
            val display = BalanceDisplay.fromResponse(response)

            // Check if balance is low
            if (display.totalBalance < LOW_BALANCE_THRESHOLD) {
                sendLowBalanceNotification(display.totalBalance)
            }

            // Send regular balance notification
            sendBalanceNotification(display)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun sendBalanceNotification(balance: BalanceDisplay) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DeepSeek 余额")
            .setContentText("当前余额: ¥${String.format("%.2f", balance.totalBalance)}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        """
                        总余额: ¥${String.format("%.2f", balance.totalBalance)}
                        赠送余额: ¥${String.format("%.2f", balance.grantedBalance)}
                        充值余额: ¥${String.format("%.2f", balance.toppedUpBalance)}
                        账户状态: ${if (balance.isAvailable) "正常" else "不可用"}
                        """.trimIndent()
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun sendLowBalanceNotification(balance: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("DeepSeek 余额不足")
            .setContentText("余额仅剩 ¥${String.format("%.2f", balance)}，请及时充值")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(LOW_BALANCE_NOTIFICATION_ID, notification)
    }
}
