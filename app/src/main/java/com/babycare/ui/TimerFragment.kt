// BabyCare/app/src/main/java/com/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.babycare.databinding.FragmentTimerBinding
import com.babycare.util.AudioPlayer
import com.babycare.util.CountdownOverlay
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 计时页面。UI 层：展示倒计时、悬浮窗、处理提醒弹窗/音频/震动。
 * 所有业务逻辑委托给 [CountdownViewModel]。
 */
class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CountdownViewModel by viewModels()

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var alertDialog: androidx.appcompat.app.AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())

    // 周期性震动（每1分钟一次，30秒后开始）
    private var vibrationJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !CountdownOverlay.hasPermission(requireContext())) {
            Toast.makeText(requireContext(), "请在设置中允许「显示悬浮窗」以显示倒计时悬浮窗", Toast.LENGTH_LONG).show()
        }

        setupTabs()
        setupUI()
        observeState()
        observeEvents()
    }

    // ═══════════════════ Tab切换 ═══════════════════

    private fun setupTabs() {
        with(binding.tabLayout) {
            addTab(newTab().setText("⏰ 时间"))
            addTab(newTab().setText("📊 统计"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val isTime = tab?.position == 0
                    binding.scrollTime.visibility = if (isTime) View.VISIBLE else View.GONE
                    binding.scrollStats.visibility = if (isTime) View.GONE else View.VISIBLE
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    // ═══════════════════ UI 绑定 ═══════════════════

    private fun setupUI() {
        val savedInterval = viewModel.uiState.value.intervalMinutes
        if (savedInterval > 0) binding.etInterval.setText(savedInterval.toString())
        else binding.etInterval.text?.clear()

        binding.btnSetInterval.setOnClickListener {
            val mins = binding.etInterval.text.toString().toIntOrNull()
            if (mins == null || mins < 1) {
                binding.etInterval.error = "至少1分钟"
                return@setOnClickListener
            }
            viewModel.setInterval(mins)
        }

        binding.btnPause.setOnClickListener {
            if (viewModel.uiState.value.isPaused) viewModel.resume()
            else viewModel.pause()
        }

        binding.btnFeedNow.setOnClickListener {
            val isBreast = binding.rbBreast.isChecked
            val volume = if (!isBreast) binding.etVolume.text.toString().toIntOrNull() else null
            viewModel.feedNow(isBreast, volume)
            binding.etVolume.text?.clear()
        }

        binding.btnStopAudio.setOnClickListener { dismissAlert() }

        binding.rbBreast.setOnCheckedChangeListener { _, checked ->
            binding.etVolume.isEnabled = !checked
            if (checked) binding.etVolume.text?.clear()
        }
        binding.rbFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.etVolume.isEnabled = true
        }

        binding.btnSaveFormula.setOnClickListener {
            val text = binding.etCustomFormula.text.toString()
            val ml = text.toIntOrNull()
            viewModel.saveCustomFormula(ml ?: 0)
            val msg = if (ml != null && ml > 0) "建议量已更新为 ${ml}ml" else "已恢复默认建议量"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            binding.etCustomFormula.text?.clear()
        }
    }

    // ═══════════════════ 状态观察 ═══════════════════

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvCountdown.text = state.countdownText
                    binding.tvEstimatedTime.text = state.estimatedTimeText
                    binding.tvCountdownLabel.text = state.labelText
                    binding.tvTodayBreastCount.text = state.todayBreastCount.toString()
                    binding.tvTodayFormulaCount.text = state.todayFormulaCount.toString()
                    binding.tvTodayFormulaAmount.text = state.todayFormulaAmount.toString()
                    binding.tvSuggestedFormula.text = state.suggestedFormula
                    binding.tvGoalRemaining.text = state.formulaRemaining
                    binding.tvLastBreastTime.text = state.lastBreastTime
                    binding.tvLastBreastDetail.text = "🤱 上次母乳 · ${state.lastBreastDetail}"
                    binding.tvLastFormulaTime.text = state.lastFormulaTime
                    binding.tvLastFormulaDetail.text = "🍼 上次配方奶 · ${state.lastFormulaDetail}"
                    binding.btnPause.text = if (state.isPaused) "▶️ 继续" else "⏸️ 暂停"
                    binding.btnPause.isEnabled = state.isPauseEnabled

                    // 倒计时进行中 → 显示悬浮窗
                    if (state.isPauseEnabled && !state.isPaused) {
                        CountdownOverlay.show(requireContext(), "⏰ ${state.countdownText}")
                    } else {
                        CountdownOverlay.hide()
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        CountdownEvent.TriggerAlert -> triggerAlert()
                        CountdownEvent.DismissAlert -> dismissAlert()
                    }
                }
            }
        }
    }

    // ═══════════════════ 提醒 ═══════════════════

    private fun triggerAlert() {
        // 隐藏悬浮窗
        CountdownOverlay.hide()

        val settings = com.babycare.data.SettingsManager(requireContext())
        val vibrateDuration = settings.getVibrateDuration()
        val vibrateInterval = settings.getVibrateInterval()

        // === 震动模式 ===
        // 阶段1：持续震动 vibrateDuration 毫秒
        try {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = android.os.VibrationEffect.createWaveform(longArrayOf(vibrateDuration), -1)
                vibrator.vibrate(effect)
            } else {
                vibrator.vibrate(longArrayOf(0, vibrateDuration), -1)
            }
        } catch (e: Exception) {
            android.util.Log.w("TimerFragment", "阶段1震动失败", e)
        }

        // 阶段2：震动结束后开始每 vibrateInterval 毫秒震动一次
        handler.postDelayed({
            startPeriodicVibration(vibrateInterval)
        }, vibrateDuration)

        // 播放音频（重复指定次数后自动停止）
        val audioPath = settings.getCustomAudioPath()
        val repeatCount = settings.getAudioRepeatCount()
        mediaPlayer = AudioPlayer.playWithRepeatCount(requireContext(), audioPath, repeatCount) {
            // 播放完毕自动停止音频，不关闭弹窗
            handler.post { binding.audioControlBar.visibility = View.GONE }
        }
        binding.audioControlBar.visibility = View.VISIBLE

        // 弹窗：用户点击「我知道了」才续时
        alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🍼 喂奶时间到！")
            .setMessage("宝宝该喂奶了")
            .setCancelable(false)
            .setPositiveButton("我知道了") { _: DialogInterface?, _: Int ->
                viewModel.confirmTimerFinished()
                dismissAlert()
            }
            .create()
            .also { it.show() }
    }

    /** 每 vibrateInterval 毫秒震动一次（短促） */
    private fun startPeriodicVibration(intervalMs: Long) {
        vibrationJob?.cancel()
        vibrationJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                try {
                    val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = android.os.VibrationEffect.createWaveform(longArrayOf(500, 200, 500), -1)
                        vibrator.vibrate(effect)
                    } else {
                        vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TimerFragment", "周期震动失败", e)
                }
                delay(intervalMs)
            }
        }
    }

    private fun dismissAlert() {
        handler.removeCallbacksAndMessages(null)
        vibrationJob?.cancel()
        vibrationJob = null
        alertDialog?.dismiss()
        alertDialog = null
        stopAudio()
        try {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.cancel()
        } catch (e: Exception) {
            android.util.Log.w("TimerFragment", "取消震动失败", e)
        }
        binding.audioControlBar.visibility = View.GONE
    }

    private fun stopAudio() {
        try { mediaPlayer?.stop() } catch (e: Exception) {
            android.util.Log.w("TimerFragment", "停止音频失败", e)
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroyView() {
        CountdownOverlay.hide()
        handler.removeCallbacksAndMessages(null)
        vibrationJob?.cancel()
        vibrationJob = null
        stopAudio()
        alertDialog?.dismiss()
        alertDialog = null
        _binding = null
        super.onDestroyView()
    }
}
