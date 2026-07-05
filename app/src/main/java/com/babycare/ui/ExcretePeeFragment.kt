// BabyCare/app/src/main/java/com/babycare/ui/ExcretePeeFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.ExcreteRecord
import com.babycare.databinding.FragmentExcretePeeBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ExcretePeeFragment : Fragment() {
    private var _binding: FragmentExcretePeeBinding? = null
    private val binding get() = _binding!!
    private val excreteDao by lazy { (requireActivity().application as BabyCareApp).database.excreteDao() }
    private val adapter = PeeAdapter { record -> deleteRecord(record) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExcretePeeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnQuickPee.setOnClickListener { addPeeRecord() }

        lifecycleScope.launch {
            excreteDao.getAll().collect { records ->
                val pee = records.filter { it.type == "pee" }
                adapter.submitList(pee)
                binding.emptyView.visibility = if (pee.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun addPeeRecord() {
        val noteInput = EditText(requireContext()).apply {
            hint = "备注（可选）"
            setTextColor(resources.getColor(android.R.color.black, null))
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("💧 排尿记录")
            .setView(noteInput)
            .setPositiveButton("确认") { _, _ ->
                lifecycleScope.launch {
                    val latest = excreteDao.getLatest()
                    val now = System.currentTimeMillis()
                    val diff = latest?.let { now - it.timestamp }
                    excreteDao.insert(ExcreteRecord(
                        type = "pee", note = noteInput.text.toString().takeIf { it.isNotBlank() },
                        timestamp = now, diff = diff
                    ))
                    Toast.makeText(requireContext(), "已记录排尿", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteRecord(record: ExcreteRecord) {
        lifecycleScope.launch { excreteDao.delete(record) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class PeeAdapter(
        private val onDelete: (ExcreteRecord) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<PeeAdapter.VH>() {
        private var records = emptyList<ExcreteRecord>()
        fun submitList(list: List<ExcreteRecord>) { records = list; notifyDataSetChanged() }
        override fun getItemCount() = records.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemBinding = com.babycare.databinding.ItemRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(itemBinding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) { holder.bind(records[position]) }
        inner class VH(private val itemBinding: com.babycare.databinding.ItemRecordBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(r: ExcreteRecord) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                itemBinding.tvTime.text = sdf.format(Date(r.timestamp))
                val notePart = if (!r.note.isNullOrBlank()) " · ${r.note}" else ""
                itemBinding.tvDetail.text = "💧 小便$notePart"
                val diffStr = r.diff?.let {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                    val hours = minutes / 60
                    if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
                } ?: ""
                itemBinding.tvInterval.text = diffStr
                itemBinding.btnDelete.setOnClickListener { onDelete(r) }
            }
        }
    }
}