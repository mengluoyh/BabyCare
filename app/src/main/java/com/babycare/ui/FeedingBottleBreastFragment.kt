// BabyCare/app/src/main/java/com/babycare/ui/FeedingBottleBreastFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.databinding.FragmentFeedingBottleBreastBinding
import com.babycare.util.Constants
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.TimeUnit

class FeedingBottleBreastFragment : Fragment(), FeedingRecordsFragment.Paginable {
    private var _binding: FragmentFeedingBottleBreastBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy { (requireActivity().application as BabyCareApp).database.feedingDao() }
    private val adapter = BottleBreastAdapter(
        onDelete = { record -> deleteRecord(record) },
        onEdit = { record -> showEditDialog(record) }
    )
    private var allRecords = listOf<FeedingRecord>()
    private var currentPage = 0
    private val pageSize = 4

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedingBottleBreastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            feedingDao.getAll().collect { records ->
                allRecords = records.filter { it.feedType == Constants.FEED_BOTTLE_BREAST }
                currentPage = 0
                updateList()
            }
        }
    }

    private fun updateList() {
        val start = currentPage * pageSize
        val end = (start + pageSize).coerceAtMost(allRecords.size)
        val pageRecords = if (allRecords.isEmpty()) emptyList() else allRecords.subList(start, end)
        adapter.submitList(pageRecords)
        binding.emptyView.visibility = if (allRecords.isEmpty()) View.VISIBLE else View.GONE
        // Notify parent of page change
        (parentFragment as? FeedingRecordsFragment)?.onPageChanged(
            currentPage, getTotalPages(), pageSize
        )
    }

    override fun goToPage(page: Int) {
        val total = getTotalPages()
        if (page < 0 || page >= total) return
        currentPage = page
        updateList()
    }

    override fun getCurrentPage() = currentPage
    override fun getTotalPages() = ((allRecords.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    override fun getPageSize() = pageSize

    private fun deleteRecord(record: FeedingRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除确认")
            .setMessage("确定删除此条瓶喂母乳记录？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    feedingDao.softDelete(record.id, System.currentTimeMillis())
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditDialog(record: FeedingRecord) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            com.babycare.R.layout.dialog_edit_feeding, null
        )
        val etVolume = dialogView.findViewById<android.widget.EditText>(com.babycare.R.id.etEditVolume)
        val etDate = dialogView.findViewById<android.widget.EditText>(com.babycare.R.id.etEditDate)
        val etTime = dialogView.findViewById<android.widget.EditText>(com.babycare.R.id.etEditTime)
        val rbBreast = dialogView.findViewById<android.widget.RadioButton>(com.babycare.R.id.rbEditBreast)
        val rbBottleBreast = dialogView.findViewById<android.widget.RadioButton>(com.babycare.R.id.rbEditBottleBreast)
        val rbFormula = dialogView.findViewById<android.widget.RadioButton>(com.babycare.R.id.rbEditFormula)

        val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
        val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        var editTimestamp = record.timestamp

        // ⭐ 先设 listener，再设初始值，确保初始勾选触发回调
        rbBreast.setOnCheckedChangeListener { _, checked ->
            etVolume.isEnabled = false
            if (checked) etVolume.text?.clear()
        }
        rbBottleBreast.setOnCheckedChangeListener { _, checked ->
            etVolume.isEnabled = checked
            if (checked) etVolume.hint = "瓶喂母乳量 (ml)"
        }
        rbFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                etVolume.isEnabled = true
                etVolume.hint = "配方奶量 (ml)"
            }
        }

        // 根据记录类型还原 Radio 状态（会触发 listener）
        when (record.feedType) {
            Constants.FEED_BREAST -> rbBreast.isChecked = true
            Constants.FEED_BOTTLE_BREAST -> rbBottleBreast.isChecked = true
            else -> rbFormula.isChecked = true
        }
        etVolume.setText(record.volume?.toString() ?: "")
        etDate.setText(DATE_FMT.format(Instant.ofEpochMilli(record.timestamp)))
        etTime.setText(TIME_FMT.format(Instant.ofEpochMilli(record.timestamp)))

        etDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = editTimestamp }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d, 12, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                editTimestamp = cal.timeInMillis
                etDate.setText(DATE_FMT.format(cal.toInstant()))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        etTime.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = editTimestamp }
            TimePickerDialog(requireContext(), { _, h, m ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                editTimestamp = cal.timeInMillis
                etTime.setText(TIME_FMT.format(cal.toInstant()))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        builder.setTitle("编辑喂养记录")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val feedType = when {
                    rbBreast.isChecked -> Constants.FEED_BREAST
                    rbBottleBreast.isChecked -> Constants.FEED_BOTTLE_BREAST
                    else -> Constants.FEED_FORMULA
                }
                val needsVolume = Constants.needsVolume(feedType)
                val volume = if (needsVolume) etVolume.text.toString().toIntOrNull() else null
                if (needsVolume && volume == null) {
                    Toast.makeText(requireContext(), "请输入奶量", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val updated = record.copy(
                        feedType = feedType,
                        volume = volume,
                        timestamp = editTimestamp,
                        lastModified = System.currentTimeMillis()
                    )
                    feedingDao.upsert(updated)
                    Toast.makeText(requireContext(), "✅ 已更新", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class BottleBreastAdapter(
        private val onDelete: (FeedingRecord) -> Unit,
        private val onEdit: (FeedingRecord) -> Unit
    ) : ListAdapter<FeedingRecord, BottleBreastAdapter.VH>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemBinding = com.babycare.databinding.ItemRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(itemBinding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        inner class VH(private val itemBinding: com.babycare.databinding.ItemRecordBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(r: FeedingRecord) {
                itemBinding.tvTime.text = DATE_FMT.format(Instant.ofEpochMilli(r.timestamp))
                itemBinding.tvDetail.text = "${Constants.feedTypeIcon(Constants.FEED_BOTTLE_BREAST)} ${Constants.feedTypeLabel(Constants.FEED_BOTTLE_BREAST)} ${r.volume ?: 0} ml"
                val diffStr = r.diff?.let {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                    val hours = minutes / 60
                    if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
                } ?: ""
                itemBinding.tvInterval.text = diffStr
                itemBinding.btnDelete.setOnClickListener { onDelete(r) }
                itemBinding.btnEdit.setOnClickListener { onEdit(r) }
            }
        }

        companion object {
            private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FeedingRecord>() {
        override fun areItemsTheSame(old: FeedingRecord, new: FeedingRecord) = old.id == new.id
        override fun areContentsTheSame(old: FeedingRecord, new: FeedingRecord) = old == new
    }
}
