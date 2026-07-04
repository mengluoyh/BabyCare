// BabyCare/app/src/main/java/com/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.data.SettingsManager
import com.babycare.databinding.FragmentTimerBinding
import com.babycare.service.AlarmScheduler
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
    private val babyDao by lazy { (requireActivity().application as BabyCareApp).database.babyDao() }
    private var birthDate: Long = 0

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
        refreshStats()
        loadBabyProfile()
    }

    private fun refreshStats() {
        lifecycleScope.launch {
            val (todayStart, todayEnd) = AgeCalculator.getTodayRange()
            val breast = feedingDao.getBreastCountBetween(todayStart, todayEnd)
            val formula = feedingDao.getFormulaTotalBetween(todayStart, todayEnd)
            binding.tvBreastCount.text = breast.toString()
            binding.tvFormulaTotal.text = formula.toString()
        }
    }

    private fun setupUI() {
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
            // 需求3：母乳不暂停也不重置，配方奶才重置并自动重开
            if (!isBreast) {
                resetAndClearTimer()
                intervalMinutes = settings.getInterval()
                nextFeedTime = System.currentTimeMillis() + intervalMinutes * 60_000L
                settings.saveNextFeedTime(nextFeedTime)
                alarmScheduler.scheduleAlarm(nextFeedTime)
                binding.btnPause.isEnabled = true
                binding.tvCountdownLabel.text = "下次定时喂奶倒计时"
                startCountdown()
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

        // 配方奶建议量保存
        binding.btnSaveFormula.setOnClickListener {
            val text = binding.etCustomFormula.text.toString()
            val ml = text.toIntOrNull()
            if (ml != null && ml > 0) {
                settings.saveCustomFormulaSuggestion(ml)
                Toast.makeText(requireContext(), "建议量已更新为 ${ml}ml", Toast.LENGTH_SHORT).show()
                binding.etCustomFormula.text?.clear()
                if (birthDate > 0) updateSuggestion()
            } else {
                settings.saveCustomFormulaSuggestion(0)
                Toast.makeText(requireContext(), "已恢复默认建议量", Toast.LENGTH_SHORT).show()
                binding.etCustomFormula.text?.clear()
                if (birthDate > 0) updateSuggestion()
            }
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

    // 需求2：倒计时结束后自动按设定间隔重新计时
    private fun timerFinished() {
        cancelTimer()
        triggerAlert()
        saveFeedingRecord("auto", "formula", null)
        // 自动重新开始计时
        intervalMinutes = settings.getInterval()
        nextFeedTime = System.currentTimeMillis() + intervalMinutes * 60_000L
        settings.saveNextFeedTime(nextFeedTime)
        alarmScheduler.scheduleAlarm(nextFeedTime)
        binding.btnPause.isEnabled = true
        binding.tvCountdownLabel.text = "⏰ 自动续时中，下次喂奶倒计时"
        startCountdown()
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
        binding.tvCountdownLabel.text = "已暂停"
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
                binding.tvCountdownLabel.text = "已暂停"
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

    // ═══════════════════ 配方奶建议量 ═══════════════════

    private fun loadBabyProfile() {
        lifecycleScope.launch {
            val profile = babyDao.getProfileSync()
            if (profile != null && profile.birthDate > 0) {
                birthDate = profile.birthDate
                updateSuggestion()
            } else {
                binding.tvSuggestedFormula.text = "-- ml"
            }
        }
    }

    private fun updateSuggestion() {
        if (birthDate <= 0) return
        val (months, _, _) = AgeCalculator.calculateAge(birthDate)
        val custom = settings.getCustomFormulaSuggestion()
        val suggested = if (custom > 0) custom else AgeCalculator.getSuggestedFormula(months)
        binding.tvSuggestedFormula.text = "${suggested} ml"
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