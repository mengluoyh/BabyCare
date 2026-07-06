// BabyCare/app/src/main/java/com/babycare/util/SyncWorker.kt
package com.babycare.util

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager 定时同步 Worker
 * 通过 SettingsManager 控制开关和间隔。
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = SyncEngine.sync(applicationContext)
            if (result.isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "auto_sync"

        /** 根据当前设置调度或取消定时同步 */
        fun schedule(context: Context) {
            val settings = com.babycare.data.SettingsManager(context)
            if (!settings.isAutoSyncEnabled()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }
            val interval = settings.getAutoSyncInterval().coerceIn(1, 24).toLong()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 取消定时同步 */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}