// BabyCare/app/src/main/java/com/babycare/ui/CustomRecordFragment.kt
package com.babycare.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.databinding.FragmentCustomRecordBinding
import com.babycare.util.Constants
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

class CustomRecordFragment : Fragment() {
    private var _binding: FragmentCustomRecordBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy {
        (requireActivity().application as BabyCareApp).database.feedingDao()
    }
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /** 用户选择的补录时间戳 */
    private var selectedTimestamp: Long = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // ⭐ 先设 listener，再设初始值，确保初始勾选触发回调
        binding.rbCustomBreast.setOnCheckedChangeListener { _, checked ->
            binding.etCustomVolume.isEnabled = false
            if (checked) binding.etCustomVolume.text?.clear()
        }
        binding.rbCustomBottleBreast.setOnCheckedChangeListener { _, checked ->
            binding.etCustomVolume.isEnabled = checked
            if (checked) binding.etCustomVolume.hint = "瓶喂母乳量 (ml)"
        }
        binding.rbCustomFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.etCustomVolume.isEnabled = true
                binding.etCustomVolume.hint = "配方奶量 (ml)"
            }
        }

        // 默认配方奶，主动触发 listener 初始化 volume 字段
        binding.rbCustomFormula.isChecked = true

        // 初始化日期/时间为当前时间
        val now = Calendar.getInstance()
        selectedTimestamp = now.timeInMillis
        binding.etCustomDate.setText(DATE_FMT.format(now.toInstant()))
        binding.etCustomTime.setText(TIME_FMT.format(now.toInstant()))

        // 日期选择器
        binding.etCustomDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                selectedTimestamp = cal.timeInMillis
                binding.etCustomDate.setText(DATE_FMT.format(cal.toInstant()))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 时间选择器
        binding.etCustomTime.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
            TimePickerDialog(requireContext(), { _, h, m ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                selectedTimestamp = cal.timeInMillis
                binding.etCustomTime.setText(TIME_FMT.format(cal.toInstant()))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        binding.btnSaveCustomRecord.setOnClickListener {
            val feedType = when {
                binding.rbCustomBreast.isChecked -> Constants.FEED_BREAST
                binding.rbCustomBottleBreast.isChecked -> Constants.FEED_BOTTLE_BREAST
                else -> Constants.FEED_FORMULA
            }
            val needsVolume = Constants.needsVolume(feedType)
            val volume = if (needsVolume) binding.etCustomVolume.text.toString().toIntOrNull() else null
            if (needsVolume && volume == null) {
                Toast.makeText(requireContext(), "请输入奶量", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveFeedingRecord(feedType, volume)
        }
    }

    private fun saveFeedingRecord(feedType: String, volume: Int?) {
        lifecycleScope.launch {
            val prev = feedingDao.getLatest()
            val diff = prev?.timestamp?.let { selectedTimestamp - it }
            val record = FeedingRecord(
                type = "manual",
                feedType = feedType,
                volume = if (Constants.needsVolume(feedType)) volume else null,
                timestamp = selectedTimestamp,
                diff = diff,
                lastModified = System.currentTimeMillis()
            )
            feedingDao.insert(record)
            val label = Constants.feedTypeLabel(feedType) + if (volume != null) " ${volume}ml" else ""
            Toast.makeText(requireContext(), "补录成功：$label", Toast.LENGTH_SHORT).show()
            binding.etCustomVolume.text?.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}