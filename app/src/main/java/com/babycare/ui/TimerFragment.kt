// BabyCare/app/src/main/java/com/babycare/ui/TimerFragment.kt
package com.babycare.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.babycare.databinding.FragmentTimerBinding
import com.babycare.util.Constants
import com.babycare.util.SyncEngine
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 计时页面。展示倒计时、喂养统计。
 * 倒计时结束后自动记录配方奶、弹窗提醒、循环播报音频。
 */
class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CountdownViewModel by activityViewModels()

    private var alertDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 恢复之前保存的输入框内容（Activity级 ViewModel 跨导航不丢失）
        val form = viewModel.loadFormState()
        if (!form.savedIntervalText.isNullOrEmpty()) {
            binding.etInterval.setText(form.savedIntervalText)
        }
        if (!form.savedVolumeText.isNullOrEmpty()) {
            binding.etVolume.setText(form.savedVolumeText)
        }
        if (!form.savedCustomFormulaText.isNullOrEmpty()) {
            binding.etCustomFormula.setText(form.savedCustomFormulaText)
        }
        if (form.savedFeedType != null) {
            when (form.savedFeedType) {
                Constants.FEED_BREAST -> binding.rbBreast.isChecked = true
                Constants.FEED_BOTTLE_BREAST -> binding.rbBottleBreast.isChecked = true
                else -> binding.rbFormula.isChecked = true
            }
        }

        setupTabs()
        setupUI()
        setupRefresh()
        observeState()
        observeEvents()
    }

    // ═══════════════════ 下拉刷新 ═══════════════════

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    val result = SyncEngine.sync(requireContext())
                    result.onSuccess {
                        val parts = mutableListOf<String>()
                        if (it.pushed > 0) parts.add("上传 ${it.pushed} 条")
                        if (it.pulled > 0) parts.add("下载 ${it.pulled} 条")
                        val msg = if (parts.isEmpty()) "✅ 同步完成，无新数据" else "✅ 同步成功：${parts.joinToString("、")}"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(requireContext(), "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
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
            val feedType = when {
                binding.rbBreast.isChecked -> Constants.FEED_BREAST
                binding.rbBottleBreast.isChecked -> Constants.FEED_BOTTLE_BREAST
                else -> Constants.FEED_FORMULA
            }
            val needsVolume = Constants.needsVolume(feedType)
            val volume = if (needsVolume) binding.etVolume.text.toString().toIntOrNull() else null
            viewModel.feedNow(feedType, volume)
            binding.etVolume.text?.clear()
        }

        binding.rbBreast.setOnCheckedChangeListener { _, checked ->
            binding.etVolume.isEnabled = false
            if (checked) binding.etVolume.text?.clear()
        }
        binding.rbBottleBreast.setOnCheckedChangeListener { _, checked ->
            binding.etVolume.isEnabled = checked
            if (checked) binding.etVolume.hint = "瓶喂母乳量 (ml)"
        }
        binding.rbFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.etVolume.isEnabled = true
                binding.etVolume.hint = "配方奶量 (ml)"
            }
        }

        // 主动触发配方奶的 listener，确保 volume 字段初始化正确
        binding.rbFormula.isChecked = true

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
                    binding.tvTodayBottleBreastCount.text = state.todayBottleBreastAmount.toString()
                    binding.tvSuggestedFormula.text = state.suggestedFormula
                    binding.tvGoalRemaining.text = state.formulaRemaining
                    binding.tvLastBreastTime.text = state.lastBreastTime
                    binding.tvLastBreastDetail.text = "🤱 上次亲喂 · ${state.lastBreastDetail}"
                    binding.tvLastBottleBreastTime.text = state.lastBottleBreastTime
                    binding.tvLastBottleBreastDetail.text = "🍶 上次瓶喂母乳 · ${state.lastBottleBreastDetail}"
                    binding.tvLastFormulaTime.text = state.lastFormulaTime
                    binding.tvLastFormulaDetail.text = "🍼 上次配方奶 · ${state.lastFormulaDetail}"
                    binding.btnPause.text = if (state.isPaused) "▶️ 继续" else "⏸️ 暂停"
                    binding.btnPause.isEnabled = state.isPauseEnabled
                }
            }
        }
    }

    // ═══════════════════ 事件监听 ═══════════════════

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        CountdownEvent.TriggerAlert -> showAlertDialog()
                        CountdownEvent.DismissAlert -> dismissAlert()
                        is CountdownEvent.ShowToast -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ═══════════════════ 喂奶提醒弹窗 ═══════════════════

    private fun showAlertDialog() {
        alertDialog?.dismiss()
        alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🍼 喂奶时间到！")
            .setMessage("喂喂喂！！！宝宝饿啦，人呢人呢，怎么还不给宝宝喂奶喝！！！宝宝要喊了哦！！！")
            .setCancelable(false)
            .setPositiveButton("来了来了") { _: DialogInterface?, _: Int ->
                viewModel.dismissAlert()
            }
            .create()
            .also { it.show() }
    }

    private fun dismissAlert() {
        alertDialog?.dismiss()
        alertDialog = null
    }

    override fun onPause() {
        super.onPause()
        // 切换界面时保存输入框内容到 Activity 级 ViewModel
        viewModel.saveFormState(
            interval = binding.etInterval.text?.toString(),
            volume = binding.etVolume.text?.toString(),
            customFormula = binding.etCustomFormula.text?.toString(),
            feedType = when {
                binding.rbBreast.isChecked -> Constants.FEED_BREAST
                binding.rbBottleBreast.isChecked -> Constants.FEED_BOTTLE_BREAST
                else -> Constants.FEED_FORMULA
            }
        )
    }

    override fun onDestroyView() {
        alertDialog?.dismiss()
        alertDialog = null
        _binding = null
        super.onDestroyView()
    }
}
