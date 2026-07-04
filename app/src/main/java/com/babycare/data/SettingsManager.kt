// BabyCare/app/src/main/java/com/example/babycare/data/SettingsManager.kt
package com.babycare.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("babycare_prefs", Context.MODE_PRIVATE)

    fun getInterval(): Int = prefs.getInt("interval_minutes", 180)
    fun saveInterval(minutes: Int) = prefs.edit().putInt("interval_minutes", minutes).apply()

    fun getNextFeedTime(): Long = prefs.getLong("next_feed_time", 0L)
    fun saveNextFeedTime(time: Long) = prefs.edit().putLong("next_feed_time", time).apply()

    fun getCustomAudioPath(): String? = prefs.getString("custom_audio_path", null)
    fun saveCustomAudioPath(path: String?) = prefs.edit().putString("custom_audio_path", path).apply()

    fun getPauseRemaining(): Long = prefs.getLong("pause_remaining", 0L)
    fun savePauseRemaining(ms: Long) = prefs.edit().putLong("pause_remaining", ms).apply()
    fun isPaused(): Boolean = prefs.getBoolean("is_paused", false)
    fun savePausedState(paused: Boolean) = prefs.edit().putBoolean("is_paused", paused).apply()
}