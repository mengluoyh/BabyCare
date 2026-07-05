// BabyCare/app/src/main/java/com/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 计时页面。UI 层：展示倒计时、处理提醒弹窗/音频。
 * 所有业务逻辑委托给 [CountdownViewModel]。
 */
class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CountdownViewModel by viewModels()

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var alertDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        binding.etInterval.setText(viewModel.uiState.value.intervalMinutes.toString())

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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvCountdown.text = state.countdownText
                    binding.tvEstimatedTime.text = state.estimatedTimeText
                    binding.tvCountdownLabel.text = state.labelText
                    binding.tvTodayBreastCount.text = state.todayBreastCount.toString()
                    binding.tvTodayFormulaAmount.text = state.todayFormulaAmount.toString()
                    binding.tvSuggestedFormula.text = state.suggestedFormula
                    // 配方奶差额
                    binding.tvFormulaRemaining.text = state.formulaRemaining
                    // 上次喂养
                    binding.tvLastBreastTime.text = state.lastBreastTime
                    binding.tvLastBreastDetail.text = "🤱 上次母乳 · ${state.lastBreastDetail}"
                    binding.tvLastFormulaTime.text = state.lastFormulaTime
                    binding.tvLastFormulaDetail.text = "🍼 上次配方奶 · ${state.lastFormulaDetail}"
                    binding.btnPause.text = if (state.isPaused) "▶️ 继续" else "⏸️ 暂停"
                    binding.btnPause.isEnabled = state.isPauseEnabled
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

    // ═══════════════════ 提醒（UI 专属） ═══════════════

    private fun triggerAlert() {
        try {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
        } catch (_: Exception) {}

        val audioPath = com.babycare.data.SettingsManager(requireContext()).getCustomAudioPath()
        mediaPlayer = AudioPlayer.playLooping(requireContext(), audioPath, 60_000L, handler) {
            dismissAlert()
        }
        binding.audioControlBar.visibility = View.VISIBLE

        alertDialog = AlertDialog.Builder(requireContext())
            .setTitle("🍼 喂奶时间到！")
            .setMessage("宝宝该喂奶了")
            .setCancelable(false)
            .setPositiveButton("我知道了") { _, _ -> dismissAlert() }
            .create()
            .also { it.show() }
    }

    private fun dismissAlert() {
        handler.removeCallbacksAndMessages(null)
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
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        stopAudio()
        alertDialog?.dismiss()
        alertDialog = null
        _binding = null
    }
}