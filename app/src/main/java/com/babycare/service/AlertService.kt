// BabyCare/app/src/main/java/com/babycare/service/AlertService.kt
package com.babycare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.babycare.MainActivity
import com.babycare.data.SettingsManager
import com.babycare.util.AudioPlayer

/**
 * 前台服务：倒计时结束后在后台处理音频播放、震动、高优通知。
 * 通知附带 fullScreenIntent 打开 MainActivity，让 TimerFragment 弹出「我知道了」对话框。
 * 用户点击"我知道了"后由 MainActivity/TimerFragment 发送停止指令。
 */
class AlertService : Service() {
    companion object {
        const val CHANNEL_ID = "alert_service"
        const val NOTIFICATION_ID = 1002
        /** Action: 打开 MainActivity 显示提醒弹窗 */
        const val ACTION_SHOW_ALERT = "com.babycare.action.SHOW_ALERT"
        /** Action: 停止提醒服务 */
        const val ACTION_DISMISS_ALERT = "com.babycare.action.DISMISS_ALERT"
        /** 服务最多运行时长（10分钟） */
        private const val MAX_RUN_DURATION = 10 * 60 * 1000L
    }

    private lateinit var settings: SettingsManager
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var periodicVibrationRunnable: Runnable? = null
    private var autoStopRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS_ALERT -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startAlert()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlert() {
        // 前台服务通知
        startForeground(NOTIFICATION_ID, buildNotification())

        // 播放音频铃声
        playAudio()

        // 开始震动
        startVibration()

        // 标记有待处理的提醒（Fragment 恢复时据此弹窗）
        settings.saveAlertPending(true)

        // 自动超时停止（防止用户忽略时服务永久运行）
        autoStopRunnable = Runnable { stopSelf() }
        handler.postDelayed(autoStopRunnable!!, MAX_RUN_DURATION)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SHOW_ALERT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🍼 喂奶时间到！")
            .setContentText("宝宝该喂奶了，点击查看")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(openPendingIntent, true)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "喂奶提醒服务",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun playAudio() {
        val audioPath = settings.getCustomAudioPath()
        val repeatCount = settings.getAudioRepeatCount()
        mediaPlayer = AudioPlayer.playWithRepeatCount(this, audioPath, repeatCount) {
            // 播放完毕，无额外操作（震动和弹窗继续）
        }
    }

    private fun startVibration() {
        val vibrateDuration = settings.getVibrateDuration()
        val vibrateInterval = settings.getVibrateInterval()

        // 阶段1：持续震动 vibrateDuration 毫秒
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(longArrayOf(vibrateDuration), -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, vibrateDuration), -1)
            }
        } catch (e: Exception) {
            android.util.Log.w("AlertService", "阶段1震动失败", e)
        }

        // 阶段2：震动结束后开始周期性短震
        handler.postDelayed({
            startPeriodicVibration(vibrateInterval)
        }, vibrateDuration)
    }

    private fun startPeriodicVibration(intervalMs: Long) {
        periodicVibrationRunnable?.let { handler.removeCallbacks(it) }
        periodicVibrationRunnable = object : Runnable {
            override fun run() {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createWaveform(longArrayOf(500, 200, 500), -1)
                        vibrator?.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(longArrayOf(0, 500, 200, 500), -1)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AlertService", "周期震动失败", e)
                }
                handler.postDelayed(this, intervalMs)
            }
        }.also { handler.postDelayed(it, intervalMs) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止音频
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            android.util.Log.w("AlertService", "停止音频失败", e)
        }
        mediaPlayer = null

        // 取消震动
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            android.util.Log.w("AlertService", "取消震动失败", e)
        }

        // 清除所有 Handler 回调
        handler.removeCallbacksAndMessages(null)
        periodicVibrationRunnable = null
        autoStopRunnable = null

        // 清除提醒标记
        settings.saveAlertPending(false)
    }
}
