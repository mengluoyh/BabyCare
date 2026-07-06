// BabyCare/app/src/main/java/com/babycare/service/AlarmReceiver.kt
package com.babycare.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 闹钟广播接收器：收到倒计时结束广播后启动 [AlertService] 前台服务，
 * 由服务处理音频播放、震动和全屏通知。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlertService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}