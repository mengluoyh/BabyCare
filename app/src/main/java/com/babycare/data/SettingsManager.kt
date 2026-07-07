// BabyCare/app/src/main/java/com/babycare/data/SettingsManager.kt
package com.babycare.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("babycare_prefs", Context.MODE_PRIVATE)

    // 定时喂奶间隔（分钟），默认0表示未设置
    fun getInterval(): Int = prefs.getInt("interval_minutes", 0)
    fun saveInterval(minutes: Int) = prefs.edit().putInt("interval_minutes", minutes).apply()

    fun getNextFeedTime(): Long = prefs.getLong("next_feed_time", 0L)
    fun saveNextFeedTime(time: Long) = prefs.edit().putLong("next_feed_time", time).apply()

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

    // ─── 提醒待处理标记 ───
    /** 倒计时结束后是否有待处理的喂奶提醒弹窗 */
    fun getAlertPending(): Boolean = prefs.getBoolean("alert_pending", false)
    fun saveAlertPending(pending: Boolean) = prefs.edit().putBoolean("alert_pending", pending).apply()

    // ─── 音频播报次数 ───
    fun getAudioRepeatCount(): Int = prefs.getInt("audio_repeat_count", 1)
    fun saveAudioRepeatCount(count: Int) = prefs.edit().putInt("audio_repeat_count", count.coerceIn(1, 10)).apply()

    // ─── 音频文件路径（SAF URI） ───
    fun getAudioFilePath(): String = prefs.getString("audio_file_path", "") ?: ""
    fun saveAudioFilePath(path: String) = prefs.edit().putString("audio_file_path", path).apply()

    // ─── 倒计时完成标记（防止重复处理） ───
    fun getTimerFinishedHandled(): Boolean = prefs.getBoolean("timer_finished_handled", false)
    fun saveTimerFinishedHandled(handled: Boolean) = prefs.edit().putBoolean("timer_finished_handled", handled).apply()

    // ─── 背景图 ───
    fun getBackgroundImagePath(): String = prefs.getString("background_image_path", "") ?: ""
    fun saveBackgroundImagePath(path: String) = prefs.edit().putString("background_image_path", path).apply()

    /** 背景图透明度 0~255，默认 255（完全不透明） */
    fun getBackgroundAlpha(): Int = prefs.getInt("background_alpha", 255)
    fun saveBackgroundAlpha(alpha: Int) = prefs.edit().putInt("background_alpha", alpha.coerceIn(0, 255)).apply()

    // ─── 自动同步开关 ───
    fun isAutoSyncEnabled(): Boolean = prefs.getBoolean("auto_sync_enabled", false)
    fun setAutoSyncEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_sync_enabled", enabled).apply()

    // ─── 自动同步间隔（小时，1~24） ───
    fun getAutoSyncInterval(): Int = prefs.getInt("auto_sync_interval", 6)
    fun setAutoSyncInterval(hours: Int) = prefs.edit().putInt("auto_sync_interval", hours.coerceIn(1, 24)).apply()

}