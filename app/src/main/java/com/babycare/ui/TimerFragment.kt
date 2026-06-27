// BabyCare/app/src/main/java/com/example/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.data.SettingsManager
import com.babycare.databinding.FragmentTimerBinding
import com.babycare.service.AlarmScheduler
import com.babycare.util.AudioPlayer
import kotlinx.coroutines.launch

class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!
    private var countDownTimer: CountDownTimer? = null
    private var nextFeedTime: Long = 0
    private var isPaused = false
    private var remainingOnPause: Long = 0
    private var intervalMinutes: Int = 180
    private var mediaPlayer: MediaPlayer? = null
    private val settings by lazy { SettingsManager(requireContext()) }
    private val alarmScheduler by lazy { AlarmScheduler(requireContext()) }
    private val feedingDao by lazy {
        (requireActivity().application as BabyCareApp).database.feedingDao()
    }

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
    }

    private fun setupUI() {
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

        binding.btnFeedNow.setOnClickListener {
            val feedType = if (binding.rbBreast.isChecked) "breast" else "formula"
            val volume = if (feedType == "formula") binding.etVolume.text.toString().toIntOrNull() else null
            saveFeedingRecord("manual", feedType, volume)
            cancelAlert()
            if (feedType == "breast") {
                if (!isPaused && nextFeedTime > System.currentTimeMillis()) pauseTimer()
            } else {
                resetAndClearTimer()
            }
            binding.etVolume.text?.clear()
        }

        binding.btnStopAudio.setOnClickListener { stopAudio() }

        binding.rbBreast.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.etVolume.isEnabled = false
                binding.etVolume.text?.clear()
            }
        }
        binding.rbFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.etVolume.isEnabled = true
        }
    }

    private fun saveFeedingRecord(type: String, feedType: String, volume: Int?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val prev = feedingDao.getLatest()
            val diff = prev?.timestamp?.let { System.currentTimeMillis() - it }
            val record = FeedingRecord(
                type = type,
                feedType = feedType,
                volume = volume,
                timestamp = System.currentTimeMillis(),
                diff = diff
            )
            feedingDao.insert(record)
        }
    }

    private fun startCountdown() {
        cancelTimer()
        val remaining = nextFeedTime - System.currentTimeMillis()
        if (remaining <= 0) {
            timerFinished()
            return
        }
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
    }

    private fun timerFinished() {
        cancelTimer()
        nextFeedTime = 0
        settings.saveNextFeedTime(0)
        alarmScheduler.cancelAlarm()
        binding.btnPause.isEnabled = false
        binding.tvCountdownLabel.text = "⏰ 倒计时已结束，点击「设置并开始」重新计时"
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
        updateDisplay(0)
    }

    private fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun triggerAlert() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)

        val audioPath = settings.getCustomAudioPath()
        mediaPlayer = AudioPlayer.play(requireContext(), audioPath) { stopAudio() }

        showAlertDialog()
    }

    private fun showAlertDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("🍼 喂奶时间到！")
            .setMessage("宝宝该喂奶了")
            .setCancelable(false)
            .setPositiveButton("我知道了") { dialog, _ ->
                dialog.dismiss()
                cancelAlert()
            }
            .show()
    }

    private fun cancelAlert() {
        stopAudio()
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.cancel()
    }

    private fun stopAudio() {
        mediaPlayer?.release()
        mediaPlayer = null
        binding.audioControlBar.visibility = View.GONE
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
        cancelTimer()
        _binding = null
    }
}