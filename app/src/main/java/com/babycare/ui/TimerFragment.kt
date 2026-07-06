// BabyCare/app/src/main/java/com/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.os.Bundle
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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * 计时页面。展示倒计时、喂养统计。
 * 倒计时结束后自动记录配方奶并重启倒计时，无需用户交互。
 */
class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CountdownViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setupUI()
        observeState()
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
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
