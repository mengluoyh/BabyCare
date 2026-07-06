// BabyCare/app/src/main/java/com/babycare/ui/ExcreteBowelFragment.kt
package com.babycare.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.babycare.BabyCareApp
import com.babycare.R
import com.babycare.data.ExcreteRecord
import com.babycare.databinding.FragmentExcreteBowelBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ExcreteBowelFragment : Fragment() {
    private var _binding: FragmentExcreteBowelBinding? = null
    private val binding get() = _binding!!
    private val excreteDao by lazy { (requireActivity().application as BabyCareApp).database.excreteDao() }
    private val adapter = BowelAdapter { record -> deleteRecord(record) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExcreteBowelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnQuickBowel.setOnClickListener { showBowelDialog() }

        lifecycleScope.launch {
            excreteDao.getAll().collect { records ->
                val bowel = records.filter { it.type == "bowel" }
                adapter.submitList(bowel)
                binding.emptyView.visibility = if (bowel.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showBowelDialog() {
        val options = arrayOf("🟤 正常", "🟡 稀便", "🟠 干硬")
        val states = arrayOf("normal", "loose", "hard")
        val noteInput = EditText(requireContext()).apply {
            hint = "备注（可选）"
            setTextColor(resources.getColor(R.color.on_background, null))
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择大便状态")
            .setView(noteInput)
            .setItems(options) { _, which ->
                addBowelRecord(states[which], noteInput.text.toString().takeIf { it.isNotBlank() })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addBowelRecord(state: String, note: String?) {
        lifecycleScope.launch {
            val latest = excreteDao.getLatest()
            val now = System.currentTimeMillis()
            val diff = latest?.let { now - it.timestamp }
            excreteDao.insert(ExcreteRecord(
                type = "bowel", state = state, note = note,
                timestamp = now, diff = diff
            ))
            Toast.makeText(requireContext(), "已记录排便", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRecord(record: ExcreteRecord) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("删除确认")
            .setMessage("确定删除此条排便记录？")
            .setPositiveButton("删除") { _: DialogInterface?, _: Int ->
                lifecycleScope.launch { excreteDao.softDelete(record.id, System.currentTimeMillis()) }
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class BowelAdapter(
        private val onDelete: (ExcreteRecord) -> Unit
    ) : ListAdapter<ExcreteRecord, BowelAdapter.VH>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemBinding = com.babycare.databinding.ItemRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(itemBinding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) { holder.bind(getItem(position)) }
        inner class VH(private val itemBinding: com.babycare.databinding.ItemRecordBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(r: ExcreteRecord) {
                itemBinding.tvTime.text = DATE_FMT.format(Date(r.timestamp))
                val stateMap = mapOf("normal" to "🟤 正常", "loose" to "🟡 稀便", "hard" to "🟠 干硬")
                val notePart = if (!r.note.isNullOrBlank()) " · ${r.note}" else ""
                itemBinding.tvDetail.text = "💩 大便 (${stateMap[r.state] ?: r.state})$notePart"
                val diffStr = r.diff?.let {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                    val hours = minutes / 60
                    if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
                } ?: ""
                itemBinding.tvInterval.text = diffStr
                itemBinding.btnDelete.setOnClickListener { onDelete(r) }
            }
        }

        companion object {
            private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ExcreteRecord>() {
        override fun areItemsTheSame(old: ExcreteRecord, new: ExcreteRecord) = old.id == new.id
        override fun areContentsTheSame(old: ExcreteRecord, new: ExcreteRecord) = old == new
    }
}