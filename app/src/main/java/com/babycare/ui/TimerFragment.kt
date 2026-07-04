// BabyCare/app/src/main/java/com/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.BabyProfile
import com.babycare.data.FeedingRecord
import com.babycare.data.ExcreteRecord
import com.babycare.data.SettingsManager
import com.babycare.databinding.FragmentTimerBinding
import com.babycare.service.AlarmScheduler
import com.babycare.util.IconChanger
import com.babycare.util.AgeCalculator
import com.babycare.util.AudioPlayer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!
    private var countDownTimer: CountDownTimer? = null
    private var nextFeedTime: Long = 0
    private var isPaused = false
    private var remainingOnPause: Long = 0
    private var intervalMinutes: Int = 180
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var alertDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val settings by lazy { SettingsManager(requireContext()) }
    private val alarmScheduler by lazy { AlarmScheduler(requireContext()) }
    private val feedingDao by lazy { (requireActivity().application as BabyCareApp).database.feedingDao() }
    private val excreteDao by lazy { (requireActivity().application as BabyCareApp).database.excreteDao() }
    private val babyDao by lazy { (requireActivity().application as BabyCareApp).database.babyDao() }

    private var birthDate: Long = 0
    private var birthLocked = false
    private var currentProfile: BabyProfile? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        intervalMinutes = settings.getInterval()
        binding.etInterval.setText(intervalMinutes.toString())
        setupUI()
        restoreState()
        loadBabyProfile()
        refreshStats()
    }

    // ═══════════════════ 宝宝年龄 ═══════════════════

    private fun loadBabyProfile() {
        lifecycleScope.launch {
            val profile = babyDao.getProfileSync()
            currentProfile = profile
            if (profile != null) {
                birthDate = profile.birthDate
                birthLocked = profile.isLocked
                updateBirthUI()
                updateAgeDisplay()
            }
        }
    }

    private fun updateBirthUI() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        binding.tvBirthDate.text = if (birthDate > 0) sdf.format(Date(birthDate)) else "未设置"
        binding.btnLockBirth.text = if (birthLocked) "🔓 已锁定" else "🔒 锁定"
        binding.btnEditBirth.isEnabled = birthLocked
    }

    private fun updateAgeDisplay() {
        if (birthDate <= 0) {
            binding.tvBabyAge.text = "👶 请先设置宝宝出生日期"
            return
        }
        val (months, weeks, days) = AgeCalculator.calculateAge(birthDate)
        val totalDays = AgeCalculator.totalDays(birthDate)

        val unit = settings.getAgeUnit()
        val text = when (unit) {
            "day" -> "当前宝宝已经 ${totalDays} 天"
            "week" -> "当前宝宝已经 ${totalDays / 7} 周 ${totalDays % 7} 天"
            "month" -> "当前宝宝已经 ${months} 月 ${weeks} 周 ${days} 天"
            else -> "当前宝宝已经 ${totalDays} 天"
        }
        binding.tvBabyAge.text = text

        // 更新建议量
        updateSuggestion(months)
    }

    private fun updateSuggestion(months: Int) {
        val custom = settings.getCustomFormulaSuggestion()
        val suggested = if (custom > 0) custom else AgeCalculator.getSuggestedFormula(months)
        binding.tvSuggestedFormula.text = "${suggested} ml"
    }

    // ═══════════════════ 刷新统计 ═══════════════════

    private fun refreshStats() {
        lifecycleScope.launch {
            val (todayStart, todayEnd) = AgeCalculator.getTodayRange()
            val breast = feedingDao.getBreastCountBetween(todayStart, todayEnd)
            val formula = feedingDao.getFormulaTotalBetween(todayStart, todayEnd)
            binding.tvBreastCount.text = breast.toString()
            binding.tvFormulaTotal.text = formula.toString()
        }
    }

    // ═══════════════════ UI 事件 ═══════════════════

    private fun setupUI() {
        // 出生日期按钮
        binding.btnEditBirth.setOnClickListener { showBirthDatePicker() }
        binding.btnLockBirth.setOnClickListener { toggleBirthLock() }

        // 年龄单位选择
        binding.rgAgeUnit.setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                com.babycare.R.id.rbAgeDay -> "day"
                com.babycare.R.id.rbAgeWeek -> "week"
                else -> "month"
            }
            settings.saveAgeUnit(unit)
            updateAgeDisplay()
        }

        // 自定义配方奶量
        binding.btnSaveFormula.setOnClickListener {
            val text = binding.etCustomFormula.text.toString()
            val ml = text.toIntOrNull()
            if (ml != null && ml > 0) {
                settings.saveCustomFormulaSuggestion(ml)
                Toast.makeText(requireContext(), "建议量已更新为 ${ml}ml", Toast.LENGTH_SHORT).show()
                binding.etCustomFormula.text?.clear()
                if (birthDate > 0) {
                    val (months, _, _) = AgeCalculator.calculateAge(birthDate)
                    updateSuggestion(months)
                }
            } else {
                settings.saveCustomFormulaSuggestion(0)
                Toast.makeText(requireContext(), "已恢复默认建议量", Toast.LENGTH_SHORT).show()
                binding.etCustomFormula.text?.clear()
                if (birthDate > 0) {
                    val (months, _, _) = AgeCalculator.calculateAge(birthDate)
                    updateSuggestion(months)
                }
            }
        }

        // 倒计时设置
        binding.btnSetInterval.setOnClickListener {
            val mins = binding.etInterval.text.toString().toIntOrNull()
            if (mins == null || mins < 1) {
                binding.etInterval.error = "至少1分钟"
                return@setOnClickListener
            }
            intervalMinutes = mins
            settings.saveInterval(mins)
            cancelTimer()
            nextFeedTime = System.currentTimeMillis() + mins * 60_000L
            settings.saveNextFeedTime(nextFeedTime)
            alarmScheduler.scheduleAlarm(nextFeedTime)
            isPaused = false
            settings.savePausedState(false)
            binding.btnPause.text = "⏸️ 暂停"
            binding.tvCountdownLabel.text = "下次定时喂奶倒计时"
            binding.btnPause.isEnabled = true
            startCountdown()
        }

        binding.btnPause.setOnClickListener {
            if (isPaused) resumeTimer() else pauseTimer()
        }

        // 快速记录喂奶
        binding.btnFeedNow.setOnClickListener {
            val isBreast = binding.rbBreast.isChecked
            val feedType = if (isBreast) "breast" else "formula"
            val volume = if (!isBreast) binding.etVolume.text.toString().toIntOrNull() else null
            saveFeedingRecord("manual", feedType, volume)
            cancelAlert()
            if (isBreast) {
                if (!isPaused && nextFeedTime > System.currentTimeMillis()) pauseTimer()
            } else {
                resetAndClearTimer()
            }
            binding.etVolume.text?.clear()
            refreshStats()
        }

        binding.btnStopAudio.setOnClickListener { cancelAlert() }

        binding.rbBreast.setOnCheckedChangeListener { _, checked ->
            binding.etVolume.isEnabled = !checked
            if (checked) binding.etVolume.text?.clear()
        }
        binding.rbFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.etVolume.isEnabled = true
        }

        // 图标切换
        initIconSwitcher()
    }

    // ═══════════════════ 图标切换 ═══════════════════

    private fun initIconSwitcher() {
        // 显示当前图标
        val current = IconChanger.getCurrentIcon(requireContext())
        updateIconLabel(current)

        binding.btnIconOrange.setOnClickListener { switchIcon(IconChanger.ICON_ORANGE) }
        binding.btnIconBlue.setOnClickListener { switchIcon(IconChanger.ICON_BLUE) }
        binding.btnIconPink.setOnClickListener { switchIcon(IconChanger.ICON_PINK) }
    }

    private fun switchIcon(iconName: String) {
        IconChanger.setIcon(requireContext(), iconName)
        updateIconLabel(iconName)
        Toast.makeText(requireContext(), "图标已切换为${iconName}色，返回桌面查看", Toast.LENGTH_SHORT).show()
    }

    private fun updateIconLabel(iconName: String) {
        val label = when (iconName) {
            IconChanger.ICON_ORANGE -> "当前：🟠 橙色"
            IconChanger.ICON_BLUE -> "当前：🔵 蓝色"
            IconChanger.ICON_PINK -> "当前：🩷 粉色"
            else -> "当前：🟠 橙色"
        }
        binding.tvCurrentIcon.text = label
    }

    private fun showBirthDatePicker() {
        val cal = Calendar.getInstance()
        if (birthDate > 0) cal.timeInMillis = birthDate
        DatePickerDialog(requireContext(), { _, year, month, day ->
            cal.set(year, month, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            birthDate = cal.timeInMillis
            updateBirthUI()
            updateAgeDisplay()
            // 自动锁定
            if (!birthLocked) toggleBirthLock()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun toggleBirthLock() {
        birthLocked = !birthLocked
        binding.btnLockBirth.text = if (birthLocked) "🔓 已锁定" else "🔒 锁定"
        binding.btnEditBirth.isEnabled = birthLocked

        lifecycleScope.launch {
            if (currentProfile != null) {
                babyDao.upsertProfile(currentProfile!!.copy(birthDate = birthDate, isLocked = birthLocked))
            } else {
                babyDao.upsertProfile(BabyProfile(birthDate = birthDate, isLocked = birthLocked))
            }
            loadBabyProfile()
        }
    }

    // ═══════════════════ 喂奶记录 ═══════════════════

    private fun saveFeedingRecord(type: String, feedType: String, volume: Int?) {
        lifecycleScope.launch {
            val prev = feedingDao.getLatest()
            val diff = prev?.timestamp?.let { System.currentTimeMillis() - it }
            val record = FeedingRecord(
                type = type,
                feedType = feedType,
                volume = if (feedType == "formula") volume else null,
                timestamp = System.currentTimeMillis(),
                diff = diff
            )
            feedingDao.insert(record)
            Toast.makeText(requireContext(), "已记录${if (feedType == "breast") "母乳" else "配方奶"}喂养", Toast.LENGTH_SHORT).show()
            refreshStats()
        }
    }

    // ═══════════════════ 倒计时 ═══════════════════

    private fun startCountdown() {
        cancelTimer()
        val remaining = nextFeedTime - System.currentTimeMillis()
        if (remaining <= 0) {
            timerFinished()
            return
        }
        updateEstimatedTime(remaining)
        countDownTimer = object : CountDownTimer(remaining, 200) {
            override fun onTick(millisUntilFinished: Long) {
                updateDisplay(millisUntilFinished)
            }
            override fun onFinish() {
                timerFinished()
            }
        }.start()
    }

    private fun updateDisplay(remaining: Long) {
        val h = remaining / 3_600_000
        val m = (remaining % 3_600_000) / 60_000
        val s = (remaining % 60_000) / 1_000
        binding.tvCountdown.text = String.format("%02d:%02d:%02d", h, m, s)
        updateEstimatedTime(remaining)
    }

    private fun updateEstimatedTime(remainingMs: Long) {
        if (remainingMs <= 0) {
            binding.tvEstimatedTime.text = ""
            return
        }
        val estimatedClock = System.currentTimeMillis() + remainingMs
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvEstimatedTime.text = "预计 ${sdf.format(Date(estimatedClock))} 可以喂奶"
    }

    private fun timerFinished() {
        cancelTimer()
        nextFeedTime = 0
        settings.saveNextFeedTime(0)
        alarmScheduler.cancelAlarm()
        binding.btnPause.isEnabled = false
        binding.tvCountdownLabel.text = "⏰ 倒计时已结束，点击「开始」重新计时"
        binding.tvEstimatedTime.text = ""
        updateDisplay(0)
        triggerAlert()
        saveFeedingRecord("auto", "formula", null)
    }

    private fun pauseTimer() {
        if (nextFeedTime <= 0 || isPaused) return
        remainingOnPause = nextFeedTime - System.currentTimeMillis()
        isPaused = true
        settings.savePausedState(true)
        settings.savePauseRemaining(remainingOnPause)
        cancelTimer()
        alarmScheduler.cancelAlarm()
        binding.btnPause.text = "▶️ 继续"
        binding.tvCountdownLabel.text = "已暂停 (母乳喂养中)"
        updateDisplay(remainingOnPause)
    }

    private fun resumeTimer() {
        if (!isPaused) return
        remainingOnPause = settings.getPauseRemaining()
        nextFeedTime = System.currentTimeMillis() + remainingOnPause
        settings.saveNextFeedTime(nextFeedTime)
        alarmScheduler.scheduleAlarm(nextFeedTime)
        isPaused = false
        settings.savePausedState(false)
        binding.btnPause.text = "⏸️ 暂停"
        binding.tvCountdownLabel.text = "下次定时喂奶倒计时"
        binding.btnPause.isEnabled = true
        startCountdown()
    }

    private fun resetAndClearTimer() {
        cancelTimer()
        nextFeedTime = 0
        settings.saveNextFeedTime(0)
        alarmScheduler.cancelAlarm()
        isPaused = false
        settings.savePausedState(false)
        binding.btnPause.text = "⏸️ 暂停"
        binding.btnPause.isEnabled = false
        binding.tvCountdownLabel.text = "已清零，请重新设置间隔"
        binding.tvEstimatedTime.text = ""
        updateDisplay(0)
    }

    private fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    // ═══════════════════ 提醒 ═══════════════════

    private fun triggerAlert() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)

        val audioPath = settings.getCustomAudioPath()
        mediaPlayer = AudioPlayer.playLooping(requireContext(), audioPath, 60_000L, handler) {
            dismissAlert()
        }
        binding.audioControlBar.visibility = View.VISIBLE
        showAlertDialog()
    }

    private fun showAlertDialog() {
        alertDialog = AlertDialog.Builder(requireContext())
            .setTitle("🍼 喂奶时间到！")
            .setMessage("宝宝该喂奶了")
            .setCancelable(false)
            .setPositiveButton("我知道了") { _, _ -> cancelAlert() }
            .create()
        alertDialog?.show()
    }

    private fun cancelAlert() { dismissAlert() }

    private fun dismissAlert() {
        handler.removeCallbacksAndMessages(null)
        alertDialog?.dismiss()
        alertDialog = null
        stopAudio()
        try {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.cancel()
        } catch (_: Exception) {}
        binding.audioControlBar.visibility = View.GONE
    }

    private fun stopAudio() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun restoreState() {
        val savedNextTime = settings.getNextFeedTime()
        if (savedNextTime > System.currentTimeMillis()) {
            nextFeedTime = savedNextTime
            isPaused = settings.isPaused()
            if (isPaused) {
                remainingOnPause = settings.getPauseRemaining()
                binding.btnPause.text = "▶️ 继续"
                binding.tvCountdownLabel.text = "已暂停 (母乳喂养中)"
                binding.btnPause.isEnabled = true
                updateDisplay(remainingOnPause)
            } else {
                binding.btnPause.isEnabled = true
                startCountdown()
            }
        } else if (savedNextTime > 0) {
            resetAndClearTimer()
        } else {
            binding.btnPause.isEnabled = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        cancelTimer()
        stopAudio()
        alertDialog?.dismiss()
        alertDialog = null
        _binding = null
    }
}