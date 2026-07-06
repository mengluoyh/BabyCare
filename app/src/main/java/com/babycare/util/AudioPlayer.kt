// BabyCare/app/src/main/java/com/babycare/util/AudioPlayer.kt
package com.babycare.util

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.babycare.R
import java.io.File

object AudioPlayer {
    fun play(context: Context, customPath: String?, onComplete: () -> Unit): MediaPlayer? {
        return try {
            val mp = if (customPath != null && File(customPath).exists()) {
                MediaPlayer().apply { setDataSource(customPath); prepare() }
            } else {
                MediaPlayer.create(context, R.raw.alarm)
            }
            mp?.apply {
                isLooping = false
                start()
                setOnCompletionListener {
                    it.release()
                    onComplete()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 播放音频，最多持续 maxDurationMs 毫秒后自动停止 */
    fun playWithTimeout(
        context: Context,
        customPath: String?,
        maxDurationMs: Long,
        onComplete: () -> Unit
    ): MediaPlayer? {
        var completed = false
        val wrappedComplete = {
            if (!completed) {
                completed = true
                onComplete()
            }
        }
        val mp = play(context, customPath, wrappedComplete)
        if (mp != null && maxDurationMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (mp.isPlaying) {
                        mp.stop()
                        mp.release()
                        wrappedComplete()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AudioPlayer", "超时停止播放失败", e)
                }
            }, maxDurationMs)
        }
        return mp
    }

    /** 播放音频指定次数，播完自动停止 */
    fun playWithRepeatCount(
        context: Context,
        customPath: String?,
        repeatCount: Int,
        onComplete: () -> Unit
    ): MediaPlayer? {
        if (repeatCount <= 0) { onComplete(); return null }
        return try {
            val mp = if (customPath != null && File(customPath).exists()) {
                MediaPlayer().apply { setDataSource(customPath); prepare() }
            } else {
                MediaPlayer.create(context, R.raw.alarm)
            }
            mp?.apply {
                isLooping = false
                var played = 0
                setOnCompletionListener {
                    played++
                    if (played < repeatCount) {
                        try { seekTo(0); start() } catch (e: Exception) {
                            android.util.Log.w("AudioPlayer", "重播失败", e)
                            release(); onComplete()
                        }
                    } else {
                        release(); onComplete()
                    }
                }
                start()
            }
            mp
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayer", "创建播放器失败", e)
            onComplete()
            null
        }
    }

    /** 循环播放音频，maxDurationMs 毫秒后自动停止。通过传入的 handler 调度，调用方可通过 handler.removeCallbacks 取消超时 */
    fun playLooping(
        context: Context,
        customPath: String?,
        maxDurationMs: Long,
        handler: Handler,
        onTimeout: () -> Unit
    ): MediaPlayer? {
        return try {
            val mp = if (customPath != null && File(customPath).exists()) {
                MediaPlayer().apply { setDataSource(customPath); prepare() }
            } else {
                MediaPlayer.create(context, R.raw.alarm)
            }
            mp?.apply {
                isLooping = true
                start()
            }
            if (mp != null && maxDurationMs > 0) {
                handler.postDelayed({
                    try {
                        if (mp.isPlaying) {
                            mp.stop()
                            mp.release()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AudioPlayer", "超时停止循环播放失败", e)
                    }
                    onTimeout()
                }, maxDurationMs)
            }
            mp
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayer", "创建播放器失败", e)
            null
        }
    }
}