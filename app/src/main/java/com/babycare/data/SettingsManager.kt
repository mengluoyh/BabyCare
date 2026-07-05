// BabyCare/app/src/main/java/com/babycare/data/SettingsManager.kt
package com.babycare.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("babycare_prefs", Context.MODE_PRIVATE)

    // 定时喂奶间隔（分钟）
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

    // 自定义配方奶建议量（覆盖默认值）
    fun getCustomFormulaSuggestion(): Int = prefs.getInt("custom_formula_suggestion", 0)
    fun saveCustomFormulaSuggestion(ml: Int) = prefs.edit().putInt("custom_formula_suggestion", ml).apply()

    // 年龄显示单位: "day", "week", "month"
    fun getAgeUnit(): String = prefs.getString("age_unit", "day") ?: "day"
    fun saveAgeUnit(unit: String) = prefs.edit().putString("age_unit", unit).apply()

    // ─── 主题模式 ───
    fun getThemeMode(): String = prefs.getString("theme_mode", "system") ?: "system"
    fun saveThemeMode(mode: String) = prefs.edit().putString("theme_mode", mode).apply()

    // ─── 体重单位: "kg" | "jin" ───
    fun getWeightUnit(): String = prefs.getString("weight_unit", "kg") ?: "kg"
    fun saveWeightUnit(unit: String) = prefs.edit().putString("weight_unit", unit).apply()

}