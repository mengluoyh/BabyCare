// BabyCare/app/src/main/java/com/babycare/BabyCareApp.kt
package com.babycare

import android.app.Application
import com.babycare.data.AppDatabase
import com.babycare.data.SettingsManager
import com.babycare.util.SyncEngine
import com.babycare.util.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BabyCareApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }

    companion object {
        lateinit var instance: BabyCareApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 方案1：启动时静默同步（仅在开启自动同步时执行）
        if (SettingsManager(this).isAutoSyncEnabled()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SyncEngine.sync(this@BabyCareApp)
                } catch (e: Exception) {
                    android.util.Log.w("BabyCareApp", "启动同步失败: ${e.message}")
                }
            }
        }

        // 方案2：调度 WorkManager 定时同步（根据设置）
        SyncWorker.schedule(this)
    }
}