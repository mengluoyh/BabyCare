// BabyCare/app/src/main/java/com/babycare/util/CountdownNotificationHelper.kt
package com.babycare.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.babycare.R

/**
 * 管理倒计时持续通知（路径 A 改进）。
 * 在倒计时运行期间于通知栏显示剩余时间，支持操作按钮。
 */
object CountdownNotificationHelper {

    private const val CHANNEL_ID = "countdown_channel"
    private const val NOTIFICATION_ID = 1002

    /** 创建通知渠道（仅需一次） */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "喂奶倒计时",
                NotificationManager.IMPORTANCE_LOW // 低优先级，不弹出打扰
            ).apply {
                description = "显示倒计时剩余时间"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 显示或更新倒计时通知 */
    fun showCountdown(
        context: Context,
        countdownText: String,
        estimatedText: String,
        labelText: String,
        isPaused: Boolean,
        intervalMinutes: Int
    ) {
        ensureChannel(context)

        // 点击通知打开 App（使用应用入口 Activity）
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 调用广播接收器处理暂停/继续
        val pauseIntent = Intent(context, CountdownNotificationReceiver::class.java).apply {
            action = if (isPaused) "com.babycare.RESUME" else "com.babycare.PAUSE"
        }
        val pausePending = PendingIntent.getBroadcast(
            context, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 取消倒计时
        val clearIntent = Intent(context, CountdownNotificationReceiver::class.java).apply {
            action = "com.babycare.CLEAR"
        }
        val clearPending = PendingIntent.getBroadcast(
            context, 2, clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) "⏸️ 已暂停 — ${countdownText}" else "⏰ ${countdownText}"
        val text = if (isPaused) "点击「继续」恢复倒计时" else estimatedText.ifEmpty { "间隔 ${intervalMinutes} 分钟" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(!isPaused) // 倒计时中不可滑动清除
            .setAutoCancel(false)
            .setContentIntent(openPending)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "▶️ 继续" else "⏸️ 暂停",
                pausePending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "✖ 取消",
                clearPending
            )
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    /** 移除倒计时通知 */
    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }
}
