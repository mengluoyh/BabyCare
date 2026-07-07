// BabyCare/app/src/main/java/com/babycare/ui/CountdownViewModel.kt
package com.babycare.ui

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.data.SettingsManager
import com.babycare.util.AgeCalculator
import com.babycare.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 倒计时 ViewModel，管理喂奶倒计时逻辑、喂养记录写入和今日统计。
 * 倒计时结束后自动记录配方奶、弹窗提醒、循环播报音频。
 */
class CountdownViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** 预计时间格式化（java.time 线程安全） */
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }

    private val app = application as BabyCareApp
    private val settings = SettingsManager(application)
    private val feedingDao = app.database.feedingDao()
    private val babyDao = app.database.babyDao()

    // ─── UI State ──────────────────────────────────
    private val _uiState = MutableStateFlow(CountdownUiState())
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    // ─── 一次性事件（Fragment 监听，replay=1 确保恢复时能收到上次事件） ───
    private val _events = MutableSharedFlow<CountdownEvent>(replay = 1)
    val events: SharedFlow<CountdownEvent> = _events.asSharedFlow()

    // ─── 内部状态 ──────────────────────────────────
    private var nextFeedTime: Long = 0
    private var isPaused = false
    private var remainingOnPause: Long = 0
    private var intervalMinutes: Int = 180
    private var birthDate: Long = 0
    private var countDownJob: kotlinx.coroutines.Job? = null
    private var timeSinceJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var audioPlayer: MediaPlayer? = null

    init {
        intervalMinutes = settings.getInterval()
        updateState { copy(intervalMinutes = intervalMinutes) }
        restoreState()
        loadBabyProfile()
        refreshTodayStats()
    }

    // ═══════════════════ 公开操作 ═══════════════════

    /** 设置间隔并启动倒计时 */
    fun setInterval(minutes: Int) {
        intervalMinutes = minutes.coerceAtLeast(1)
        settings.saveInterval(intervalMinutes)
        updateState { copy(intervalMinutes = intervalMinutes) }
        cancelTimer()
        nextFeedTime = System.currentTimeMillis() + intervalMinutes * 60_000L
        settings.saveNextFeedTime(nextFeedTime)
        isPaused = false
        settings.savePausedState(false)
        updateState { copy(isPauseEnabled = true, labelText = "下次定时喂奶倒计时") }
        startCountdown()
    }

    /** 手动记录喂养（亲喂/瓶喂母乳/配方奶均重置倒计时） */
    fun feedNow(feedType: String, volume: Int?) {
        viewModelScope.launch {
            val prev = feedingDao.getLatest()
            val diff = prev?.timestamp?.let { System.currentTimeMillis() - it }
            val record = FeedingRecord(
                type = "manual",
                feedType = feedType,
                volume = if (Constants.needsVolume(feedType)) volume else null,
                timestamp = System.currentTimeMillis(),
                diff = diff
            )
            feedingDao.insert(record)
            refreshTodayStats()
            settings.saveTimerFinishedHandled(false)
            // 停止音频播报并关闭弹窗
            stopAudio()
            _events.emit(CountdownEvent.DismissAlert)
            // 发出Toast事件
            val label = Constants.feedTypeLabel(feedType)
            val volStr = if (volume != null) " ${volume}ml" else ""
            _events.emit(CountdownEvent.ShowToast("✅ 已记录：$label$volStr"))
            // 重置倒计时（均重置）
            cancelTimer()
            nextFeedTime = System.currentTimeMillis() + intervalMinutes * 60_000L
            settings.saveNextFeedTime(nextFeedTime)
            updateState { copy(isPauseEnabled = true, labelText = "下次定时喂奶倒计时") }
            startCountdown()
        }
    }

    fun pause() {
        if (nextFeedTime <= 0 || isPaused) return
        remainingOnPause = nextFeedTime - System.currentTimeMillis()
        isPaused = true
        settings.savePausedState(true)
        settings.savePauseRemaining(remainingOnPause)
        cancelTimer()
        updateState {
            copy(
                isPaused = true,
                labelText = "已暂停",
                countdownText = formatTime(remainingOnPause)
            )
        }
    }

    fun resume() {
        if (!isPaused) return
        remainingOnPause = settings.getPauseRemaining()
        nextFeedTime = System.currentTimeMillis() + remainingOnPause
        settings.saveNextFeedTime(nextFeedTime)
        isPaused = false
        settings.savePausedState(false)
        updateState { copy(isPaused = false, labelText = "下次定时喂奶倒计时") }
        startCountdown()
    }

    fun clearTimer() {
        cancelTimer()
        nextFeedTime = 0
        settings.saveNextFeedTime(0)
        isPaused = false
        settings.savePausedState(false)
        updateState {
            copy(
                isPauseEnabled = false,
                isPaused = false,
                labelText = "已清零，请重新设置间隔",
                countdownText = "00:00:00",
                estimatedTimeText = ""
            )
        }
    }

    /** 保存自定义配方奶建议量 */
    fun saveCustomFormula(ml: Int) {
        settings.saveCustomFormulaSuggestion(ml)
        if (birthDate > 0) updateSuggestion()
    }

    /** 更新配方奶建议量显示 */
    fun loadBabyProfile() {
        viewModelScope.launch {
            val profile = babyDao.getProfileSync()
            if (profile != null && profile.birthDate > 0) {
                birthDate = profile.birthDate
                updateSuggestion()
            } else {
                updateState { copy(suggestedFormula = "-- ml") }
            }
        }
    }

    private fun refreshTodayStats() {
        viewModelScope.launch {
            val (todayStart, todayEnd) = AgeCalculator.getTodayRange()
            val breastCount = feedingDao.getBreastCountBetween(todayStart, todayEnd)
            val formulaTotal = feedingDao.getFormulaTotalBetween(todayStart, todayEnd)
            val formulaCount = feedingDao.getFormulaCountBetween(todayStart, todayEnd)
            val bottleBreastCount = feedingDao.getBottleBreastCountBetween(todayStart, todayEnd)
            val bottleBreastAmount = feedingDao.getBottleBreastTotalBetween(todayStart, todayEnd)

            // 计算配方奶差额
            val suggestedInt = settings.getCustomFormulaSuggestion().let {
                if (it > 0) it else {
                    if (birthDate > 0) {
                        val (months, _, _) = AgeCalculator.calculateAge(birthDate)
                        AgeCalculator.getSuggestedFormula(months)
                    } else 0
                }
            }
            val remaining = if (suggestedInt > 0) {
                val diff = suggestedInt - formulaTotal
                if (diff > 0) "还差 ${diff}ml" else "✅ 已达标"
            } else ""

            updateState {
                copy(
                    todayBreastCount = breastCount,
                    todayFormulaAmount = formulaTotal,
                    todayFormulaCount = formulaCount,
                    todayBottleBreastCount = bottleBreastCount,
                    todayBottleBreastAmount = bottleBreastAmount,
                    formulaRemaining = remaining
                )
            }

            // 启动距上次喂奶计时
            startTimeSinceTracking()
        }
    }

    private fun startTimeSinceTracking() {
        timeSinceJob?.cancel()
        timeSinceJob = viewModelScope.launch {
            while (true) {
                val (breastTs, formulaTs, bottleBreastTs) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Triple(
                        feedingDao.getLatestBreastTimestamp() ?: 0L,
                        feedingDao.getLatestFormulaTimestamp() ?: 0L,
                        feedingDao.getLatestBottleBreastTimestamp() ?: 0L
                    )
                }
                val now = System.currentTimeMillis()
                val breastTimeStr = if (breastTs > 0) TIME_FMT.format(Instant.ofEpochMilli(breastTs)) else "--:--"
                val breastDetail = if (breastTs > 0) {
                    val elapsed = now - breastTs
                    formatDurationBrief(elapsed)
                } else "暂无记录"

                val bottleBreastTimeStr = if (bottleBreastTs > 0) TIME_FMT.format(Instant.ofEpochMilli(bottleBreastTs)) else "--:--"
                val bottleBreastDetail = if (bottleBreastTs > 0) {
                    val elapsed = now - bottleBreastTs
                    formatDurationBrief(elapsed)
                } else "暂无记录"

                val formulaTimeStr = if (formulaTs > 0) TIME_FMT.format(Instant.ofEpochMilli(formulaTs)) else "--:--"
                val formulaDetail = if (formulaTs > 0) {
                    val elapsed = now - formulaTs
                    formatDurationBrief(elapsed)
                } else "暂无记录"

                updateState {
                    copy(
                        lastBreastTime = breastTimeStr,
                        lastBreastDetail = breastDetail,
                        lastBottleBreastTime = bottleBreastTimeStr,
                        lastBottleBreastDetail = bottleBreastDetail,
                        lastFormulaTime = formulaTimeStr,
                        lastFormulaDetail = formulaDetail
                    )
                }
                delay(60_000) // 每分钟更新一次即可
            }
        }
    }

    /** 格式化为"x小时x分钟前"或"x分钟前" */
    private fun formatDurationBrief(ms: Long): String {
        val totalMin = ms / 60_000
        val hours = totalMin / 60
        val mins = totalMin % 60
        return when {
            hours > 0 -> "${hours}小时${mins}分钟前"
            mins > 0 -> "${mins}分钟前"
            else -> "刚刚"
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelTimer()
        timeSinceJob?.cancel()
        stopAudio()
    }

    // ═══════════════════ 内部方法 ═══════════════════

    private fun startCountdown() {
        cancelTimer()
        val remaining = nextFeedTime - System.currentTimeMillis()
        if (remaining <= 0) {
            onTimerFinished()
            return
        }
        updateEstimatedTime(remaining)
        updateDisplay(remaining)

        countDownJob = viewModelScope.launch {
            var ms = remaining
            while (ms > 0) {
                delay(200)
                ms = nextFeedTime - System.currentTimeMillis()
                if (ms <= 0) break
                updateDisplay(ms)
                updateEstimatedTime(ms)
            }
            if (ms <= 0) onTimerFinished()
        }
    }

    /** 倒计时自然结束 → 自动记录配方奶 + 弹窗提醒 + 循环播报音频 */
    private fun onTimerFinished() {
        if (settings.getTimerFinishedHandled()) return // 防止重复处理
        settings.saveTimerFinishedHandled(true)
        cancelTimer()
        // 自动记录配方奶
        viewModelScope.launch {
            val prev = feedingDao.getLatest()
            val diff = prev?.timestamp?.let { System.currentTimeMillis() - it }
            feedingDao.insert(FeedingRecord(
                type = "auto",
                feedType = Constants.FEED_FORMULA,
                volume = null,
                timestamp = System.currentTimeMillis(),
                diff = diff
            ))
            refreshTodayStats()
        }
        updateState {
            copy(
                isPauseEnabled = false,
                labelText = "⏰ 喂奶时间到！"
            )
        }
        // 开始循环播报音频
        playAlertAudioLoop()
        // 触发弹窗事件
        viewModelScope.launch { _events.emit(CountdownEvent.TriggerAlert) }
    }

    private fun cancelTimer() {
        countDownJob?.cancel()
        countDownJob = null
    }

    // ═══════════════════ 音频播报 ═══════════════════

    /** 循环播报音频（按设置次数重复播放） */
    private fun playAlertAudioLoop() {
        val audioPath = settings.getAudioFilePath()
        if (audioPath.isEmpty()) return
        val repeatCount = settings.getAudioRepeatCount() // 获取播报次数
        stopAudio()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(audioPath)
                for (i in 1..repeatCount) {
                    if (audioPlayer == null) break // 被外部停止
                    val player = MediaPlayer().apply {
                        setDataSource(getApplication<Application>(), uri)
                        prepare()
                        start()
                    }
                    audioPlayer = player
                    // 等待播放完成
                    val duration = player.duration.toLong()
                    delay(if (duration > 0) duration else 3000)
                    player.release()
                }
                audioPlayer = null
            } catch (_: Exception) {
                audioPlayer = null
            }
        }
    }

    /** 停止音频播报并释放资源 */
    private fun stopAudio() {
        try {
            audioPlayer?.stop()
        } catch (_: Exception) { }
        audioPlayer?.release()
        audioPlayer = null
    }

    /** 用户点击"来了来了"确认 → 停止播报 + 关闭弹窗 + 续时重启倒计时 */
    fun dismissAlert() {
        settings.saveTimerFinishedHandled(false)
        stopAudio()
        viewModelScope.launch { _events.emit(CountdownEvent.DismissAlert) }
        // 续时并重启倒计时
        nextFeedTime = System.currentTimeMillis() + intervalMinutes * 60_000L
        settings.saveNextFeedTime(nextFeedTime)
        updateState {
            copy(
                isPauseEnabled = true,
                labelText = "下次定时喂奶倒计时"
            )
        }
        startCountdown()
    }

    private fun updateDisplay(remainingMs: Long) {
        updateState { copy(countdownText = formatTime(remainingMs)) }
    }

    private fun updateEstimatedTime(remainingMs: Long) {
        val text = if (remainingMs <= 0) ""
        else {
            val clock = System.currentTimeMillis() + remainingMs
            "预计 ${TIME_FMT.format(Instant.ofEpochMilli(clock))} 可以喂奶"
        }
        updateState { copy(estimatedTimeText = text) }
    }

    private fun formatTime(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun restoreState() {
        val savedNextTime = settings.getNextFeedTime()
        when {
            savedNextTime > System.currentTimeMillis() -> {
                nextFeedTime = savedNextTime
                isPaused = settings.isPaused()
                if (isPaused) {
                    remainingOnPause = settings.getPauseRemaining()
                    updateState {
                        copy(
                            isPaused = true,
                            isPauseEnabled = true,
                            labelText = "已暂停",
                            countdownText = formatTime(remainingOnPause)
                        )
                    }
                } else {
                    updateState { copy(isPauseEnabled = true) }
                    startCountdown()
                }
            }
            savedNextTime > 0 && !settings.getTimerFinishedHandled() -> {
                // 倒计时已在其他界面结束，恢复时触发提醒
                onTimerFinished()
            }
            savedNextTime > 0 -> clearTimer()
            else -> {
                updateState { copy(isPauseEnabled = false) }
            }
        }
    }

    private fun updateSuggestion() {
        if (birthDate <= 0) return
        val (months, _, _) = AgeCalculator.calculateAge(birthDate)
        val custom = settings.getCustomFormulaSuggestion()
        val suggested = if (custom > 0) custom else AgeCalculator.getSuggestedFormula(months)
        updateState { copy(suggestedFormula = "$suggested ml") }
    }

    private fun updateState(transform: CountdownUiState.() -> CountdownUiState) {
        _uiState.value = _uiState.value.transform()
    }

    // ─── 表单输入持久化（跨导航不丢失） ───

    /** 从 [CountdownUiState] 读取保存的表单值 */
    fun loadFormState(): FormState {
        val s = _uiState.value
        return FormState(s.savedIntervalText, s.savedVolumeText, s.savedCustomFormulaText, s.savedFeedType)
    }

    /** 将当前表单值写入 [CountdownUiState] */
    fun saveFormState(interval: String?, volume: String?, customFormula: String?, feedType: String?) {
        _uiState.value = _uiState.value.copy(
            savedIntervalText = interval,
            savedVolumeText = volume,
            savedCustomFormulaText = customFormula,
            savedFeedType = feedType
        )
    }
}

data class FormState(
    val savedIntervalText: String?,
    val savedVolumeText: String?,
    val savedCustomFormulaText: String?,
    val savedFeedType: String?
)

// ─── 数据类型 ─────────────────────────────────────────

data class CountdownUiState(
    val countdownText: String = "00:00:00",
    val estimatedTimeText: String = "",
    val labelText: String = "已清零，请重新设置间隔",
    val isPaused: Boolean = false,
    val isPauseEnabled: Boolean = false,
    val intervalMinutes: Int = 0,
    val todayBreastCount: Int = 0,
    val todayFormulaAmount: Int = 0,
    val todayFormulaCount: Int = 0,
    val todayBottleBreastCount: Int = 0,
    val todayBottleBreastAmount: Int = 0,
    val suggestedFormula: String = "-- ml",
    val formulaRemaining: String = "",
    val lastBreastTime: String = "--:--",
    val lastBreastDetail: String = "暂无记录",
    val lastBottleBreastTime: String = "--:--",
    val lastBottleBreastDetail: String = "暂无记录",
    val lastFormulaTime: String = "--:--",
    val lastFormulaDetail: String = "暂无记录",
    // ─── 表单输入保留（Activity级ViewModel跨导航存活） ───
    val savedIntervalText: String? = null,
    val savedVolumeText: String? = null,
    val savedCustomFormulaText: String? = null,
    val savedFeedType: String? = null
)

// ─── 事件类型 ─────────────────────────────────────────

sealed class CountdownEvent {
    /** 触发喂奶提醒弹窗 */
    data object TriggerAlert : CountdownEvent()
    /** 关闭弹窗 */
    data object DismissAlert : CountdownEvent()
    /** 显示Toast消息 */
    data class ShowToast(val message: String) : CountdownEvent()
}