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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CustomRecordFragment : Fragment() {
    private var _binding: FragmentCustomRecordBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy {
        (requireActivity().application as BabyCareApp).database.feedingDao()
    }
    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())

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
        binding.rbCustomBreast.setOnCheckedChangeListener { _, checked ->
            binding.etCustomVolume.isEnabled = !checked
            if (checked) binding.etCustomVolume.text?.clear()
        }
        binding.rbCustomFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.etCustomVolume.isEnabled = true
        }

        // 初始化日期/时间为当前时间
        val now = Calendar.getInstance()
        selectedTimestamp = now.timeInMillis
        binding.etCustomDate.setText(DATE_FMT.format(now.time))
        binding.etCustomTime.setText(TIME_FMT.format(now.time))

        // 日期选择器
        binding.etCustomDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                selectedTimestamp = cal.timeInMillis
                binding.etCustomDate.setText(DATE_FMT.format(cal.time))
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
                binding.etCustomTime.setText(TIME_FMT.format(cal.time))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        binding.btnSaveCustomRecord.setOnClickListener {
            val isBreast = binding.rbCustomBreast.isChecked
            val feedType = if (isBreast) "breast" else "formula"
            val volume = if (!isBreast) binding.etCustomVolume.text.toString().toIntOrNull() else null
            if (!isBreast && volume == null) {
                Toast.makeText(requireContext(), "请输入配方奶量", Toast.LENGTH_SHORT).show()
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
                volume = if (feedType == "formula") volume else null,
                timestamp = selectedTimestamp,
                diff = diff,
                lastModified = System.currentTimeMillis()
            )
            feedingDao.insert(record)
            Toast.makeText(requireContext(), "补录成功：${if (feedType == "breast") "母乳" else "配方奶 ${volume}ml"}", Toast.LENGTH_SHORT).show()
            binding.etCustomVolume.text?.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}