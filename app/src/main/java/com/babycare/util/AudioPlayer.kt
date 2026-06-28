 // BabyCare/app/src/main/java/com/example/babycare/util/AudioPlayer.kt
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
        val mp = play(context, customPath, onComplete)
        if (mp != null && maxDurationMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (mp.isPlaying) {
                        mp.stop()
                        mp.release()
                        onComplete()
                    }
                } catch (_: Exception) {}
            }, maxDurationMs)
        }
        return mp
    }
}