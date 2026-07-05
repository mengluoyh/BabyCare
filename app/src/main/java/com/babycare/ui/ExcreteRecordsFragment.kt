// BabyCare/app/src/main/java/com/babycare/ui/ExcreteRecordsFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.babycare.BabyCareApp
import com.babycare.data.ExcreteRecord
import com.babycare.databinding.FragmentExcreteRecordsBinding
import com.babycare.util.AgeCalculator
import com.babycare.util.ChartDrawer
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
    private var chartDays = 7

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importRecords(uri)
    }

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
        binding.btnImport.setOnClickListener { importLauncher.launch("application/json") }
        binding.btnBowel.setOnClickListener { showBowelDialog() }
        binding.btnPee.setOnClickListener { addPeeRecord() }

        // 图表天数选择
        binding.rgChartDays.setOnCheckedChangeListener { _, checkedId ->
            chartDays = when (checkedId) {
                com.babycare.R.id.rbChart15d -> 15
                com.babycare.R.id.rbChart30d -> 30
                else -> 7
            }
            drawChart()
        }

        lifecycleScope.launch {
            excreteDao.getAll().collect { records ->
                adapter.submitList(records)
                binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        refreshDailyStats()
        drawChart()
    }

    private fun refreshDailyStats() {
        lifecycleScope.launch {
            val (start, end) = AgeCalculator.getTodayRange()
            val bowel = excreteDao.getBowelCountBetween(start, end)
            val pee = excreteDao.getPeeCountBetween(start, end)
            binding.tvTodayBowel.text = bowel.toString()
            binding.tvTodayPee.text = pee.toString()
        }
    }

    private fun drawChart() {
        lifecycleScope.launch {
            val end = System.currentTimeMillis()
            val start = AgeCalculator.getPastDaysStart(chartDays - 1)
            val records = excreteDao.getExcretesBetween(start, end)

            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            val dailyData = mutableMapOf<String, Pair<Int, Int>>()
            for (i in (chartDays - 1) downTo 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = end
                    add(Calendar.DAY_OF_MONTH, -i)
                }
                dailyData[sdf.format(cal.time)] = Pair(0, 0)
            }
            for (r in records) {
                val key = sdf.format(Date(r.timestamp))
                val (bowel, pee) = dailyData[key] ?: Pair(0, 0)
                if (r.type == "bowel") {
                    dailyData[key] = Pair(bowel + 1, pee)
                } else {
                    dailyData[key] = Pair(bowel, pee + 1)
                }
            }

            val totalBowel = dailyData.values.sumOf { it.first }
            val totalPee = dailyData.values.sumOf { it.second }

            ChartDrawer.draw(ChartDrawer.ChartConfig(
                context = requireContext(),
                chartContainer = binding.layoutChart,
                columnsContainer = binding.chartColumns,
                dailyData = dailyData,
                chartDays = chartDays,
                density = resources.displayMetrics.density,
                barColor1 = 0xFF795548.toInt(),   // 排便（底部，棕色）
                barColor2 = 0xFF1976D2.toInt(),   // 排尿（顶部，蓝色）
                labelColor1 = 0xFF795548.toInt(),
                labelColor2 = 0xFF1976D2.toInt(),
                legendFormat = "● 排便 %d 次    ■ 排尿 %d 次",
                total1 = totalBowel,
                total2 = totalPee
            ))

            binding.tvChartLegend.text = "● 排便 $totalBowel 次    ■ 排尿 $totalPee 次"
        }
    }

    private fun deleteRecord(record: ExcreteRecord) {
        lifecycleScope.launch {
            excreteDao.delete(record)
            refreshDailyStats()
            drawChart()
        }
    }

    private fun exportRecords() {
        lifecycleScope.launch {
            val records = excreteDao.getAllSnapshot()
            val file = ExportUtil.exportToJson(
                requireContext(),
                records,
                "excrete_${System.currentTimeMillis()}.json"
            )
            if (file != null) {
                Toast.makeText(requireContext(), "导出成功：${file.absolutePath}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importRecords(uri: Uri) {
        lifecycleScope.launch {
            val records = ExportUtil.importFromJson<ExcreteRecord>(requireContext(), uri)
            if (records.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "导入失败：文件为空或格式错误", Toast.LENGTH_SHORT).show()
                return@launch
            }
            excreteDao.insertAll(records)
            Toast.makeText(requireContext(), "导入成功：${records.size} 条记录", Toast.LENGTH_SHORT).show()
            refreshDailyStats()
            drawChart()
        }
    }

    private fun showBowelDialog() {
        val options = arrayOf("🟤 正常", "🟡 稀便", "🟠 干硬")
        val states = arrayOf("normal", "loose", "hard")

        val noteInput = EditText(requireContext()).apply {
            hint = "备注（可选）"
            setTextColor(resources.getColor(android.R.color.black, null))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("选择大便状态")
            .setView(noteInput)
            .setItems(options) { _, which ->
                addExcreteRecord("bowel", states[which], noteInput.text.toString().takeIf { it.isNotBlank() })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addPeeRecord() {
        val noteInput = EditText(requireContext()).apply {
            hint = "备注（可选）"
            setTextColor(resources.getColor(android.R.color.black, null))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("💧 排尿记录")
            .setView(noteInput)
            .setPositiveButton("确认") { _, _ ->
                addExcreteRecord("pee", null, noteInput.text.toString().takeIf { it.isNotBlank() })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addExcreteRecord(type: String, state: String?, note: String?) {
        lifecycleScope.launch {
            val latest = excreteDao.getLatest()
            val now = System.currentTimeMillis()
            val diff = latest?.let { now - it.timestamp }
            val record = ExcreteRecord(
                type = type,
                state = state,
                note = note,
                timestamp = now,
                diff = diff
            )
            excreteDao.insert(record)
            val label = if (type == "pee") "排尿" else "排便"
            Toast.makeText(requireContext(), "已记录$label", Toast.LENGTH_SHORT).show()
            refreshDailyStats()
            drawChart()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── 适配器 ──────────────────────────────────────────

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