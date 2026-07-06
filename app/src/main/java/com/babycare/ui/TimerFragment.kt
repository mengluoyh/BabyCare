// BabyCare/app/src/main/java/com/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.content.DialogInterface
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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 计时页面。UI 层：展示倒计时、弹窗提醒。
 * 提醒通知由 [com.babycare.service.AlertService] 处理。
 */
class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CountdownViewModel by viewModels()

    private var alertDialog: androidx.appcompat.app.AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val settings by lazy { com.babycare.data.SettingsManager(requireContext()) }

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

        // 检查是否有待处理的喂奶提醒（后台倒计时结束时设置的标记）
        if (settings.getAlertPending()) {
            showAlertDialog()
        }
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

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        CountdownEvent.TriggerAlert -> showAlertDialog()
                        CountdownEvent.DismissAlert -> dismissAlert()
                    }
                }
            }
        }
    }

    // ═══════════════════ 提醒（仅弹窗，无音频/震动） ═══════════════════

    /** 显示「我知道了」弹窗 */
    private fun showAlertDialog() {
        // 弹窗：用户点击「我知道了」才续时+记录
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

    private fun dismissAlert() {
        handler.removeCallbacksAndMessages(null)
        alertDialog?.dismiss()
        alertDialog = null
        // 清除提醒标记
        settings.saveAlertPending(false)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        alertDialog?.dismiss()
        alertDialog = null
        _binding = null
        super.onDestroyView()
    }
}
