// BabyCare/app/src/main/java/com/babycare/ui/ExcreteRecordsFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.ExcreteRecord
import com.babycare.databinding.FragmentExcreteRecordsBinding
import com.babycare.util.ExportUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ExcreteRecordsFragment : Fragment() {

    private var _binding: FragmentExcreteRecordsBinding? = null
    private val binding get() = _binding!!

    private val excreteDao by lazy {
        (requireActivity().application as BabyCareApp).database.excreteDao()
    }

    private val adapter = ExcreteAdapter { record -> deleteRecord(record) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExcreteRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnExport.setOnClickListener { exportRecords() }

        lifecycleScope.launch {
            excreteDao.getAll().collect { records ->
                adapter.submitList(records)
                binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deleteRecord(record: ExcreteRecord) {
        lifecycleScope.launch {
            excreteDao.delete(record)
        }
    }

    private fun exportRecords() {
        lifecycleScope.launch {
            var list = emptyList<ExcreteRecord>()
            excreteDao.getAll().collect { list = it }
            val file = ExportUtil.exportToJson(
                requireContext(),
                list,
                "excrete_${System.currentTimeMillis()}.json"
            )
            if (file != null) {
                Toast.makeText(requireContext(), "导出成功：${file.absolutePath}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // 内部适配器
    inner class ExcreteAdapter(
        private val onDelete: (ExcreteRecord) -> Unit
    ) : RecyclerView.Adapter<ExcreteAdapter.ViewHolder>() {

        private var records = emptyList<ExcreteRecord>()

        fun submitList(list: List<ExcreteRecord>) {
            records = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = com.babycare.databinding.ItemRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(records[position])
        }

        override fun getItemCount() = records.size

        inner class ViewHolder(private val binding: com.babycare.databinding.ItemRecordBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(record: ExcreteRecord) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                binding.tvTime.text = sdf.format(Date(record.timestamp))

                val typeLabel = when (record.type) {
                    "pee" -> "💧 小便"
                    else -> {
                        val stateMap = mapOf(
                            "normal" to "🟤 正常",
                            "loose" to "🟡 稀便",
                            "hard" to "🟠 干硬"
                        )
                        "💩 大便 (${stateMap[record.state] ?: record.state})"
                    }
                }
                val notePart = if (!record.note.isNullOrBlank()) " · ${record.note}" else ""
                binding.tvDetail.text = "$typeLabel$notePart"

                val diffStr = record.diff?.let {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                    val hours = minutes / 60
                    if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
                } ?: ""
                binding.tvInterval.text = diffStr

                binding.btnDelete.setOnClickListener { onDelete(record) }
            }
        }
    }
}