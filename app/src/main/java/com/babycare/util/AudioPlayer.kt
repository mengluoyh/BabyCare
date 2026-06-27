// BabyCare/app/src/main/java/com/example/babycare/util/AudioPlayer.kt
package com.babycare.util

import android.content.Context
import android.media.MediaPlayer
import java.io.File

object AudioPlayer {
    fun play(context: Context, customPath: String?, onComplete: () -> Unit): MediaPlayer? {
        return try {
            val mp = if (customPath != null && File(customPath).exists()) {
                MediaPlayer().apply { setDataSource(customPath) }
            } else {
                // 播放默认 raw 资源（需要将 alarm.mp3 放入 res/raw 文件夹）
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
}