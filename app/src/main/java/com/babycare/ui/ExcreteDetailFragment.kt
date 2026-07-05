// BabyCare/app/src/main/java/com/babycare/ui/ExcreteDetailFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.ExcreteRecord
import com.babycare.databinding.FragmentExcreteBowelBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/** 排泄详情 — 展示所有排泄记录（排便+排尿合并按时间倒排） */
class ExcreteDetailFragment : Fragment() {
    private var _binding: FragmentExcreteBowelBinding? = null
    private val binding get() = _binding!!
    private val excreteDao by lazy { (requireActivity().application as BabyCareApp).database.excreteDao() }
    private val adapter = ExcreteDetailAdapter { record -> deleteRecord(record) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExcreteBowelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.emptyView.text = "暂无排泄记录"

        lifecycleScope.launch {
            excreteDao.getAll().collect { records ->
                adapter.submitList(records) // 全部记录
                binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deleteRecord(record: ExcreteRecord) {
        lifecycleScope.launch { excreteDao.delete(record) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ExcreteDetailAdapter(
        private val onDelete: (ExcreteRecord) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ExcreteDetailAdapter.VH>() {
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
                val typeLabel = if (r.type == "pee") "💧 小便" else {
                    val stateMap = mapOf("normal" to "🟤 正常", "loose" to "🟡 稀便", "hard" to "🟠 干硬")
                    "💩 大便 (${stateMap[r.state] ?: r.state})"
                }
                val notePart = if (!r.note.isNullOrBlank()) " · ${r.note}" else ""
                itemBinding.tvDetail.text = "$typeLabel$notePart"
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