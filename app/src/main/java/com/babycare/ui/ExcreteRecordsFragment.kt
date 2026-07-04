// BabyCare/app/src/main/java/com/babycare/ui/ExcreteRecordsFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
                val day = Date(end - i * 24 * 60 * 60 * 1000L)
                dailyData[sdf.format(day)] = Pair(0, 0)
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

            val chartContainer = binding.layoutChart
            val columnsContainer = binding.chartColumns
            columnsContainer.removeAllViews()
            chartContainer.removeAllViews()
            chartContainer.addView(columnsContainer)

            val maxVal = dailyData.values.maxOfOrNull { maxOf(it.first, it.second) } ?: 1
            val density = resources.displayMetrics.density
            val barWidth = (Math.max(8, 24 - chartDays)).toInt() * density.toInt()
            val chartHeight = chartContainer.height.coerceAtLeast(100)
            val barAreaHeight = (chartHeight * 0.75f).toInt()

            // ─── 网格线 ───
            val gridLevels = listOf(0.25f, 0.5f, 0.75f, 1.0f)
            val gridContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                gravity = android.view.Gravity.BOTTOM
            }
            for (level in gridLevels) {
                val spacer = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f - level
                    )
                }
                val line = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (0.5f * density).toInt()
                    )
                    setBackgroundColor(0x15FFFFFF.toInt())
                }
                gridContainer.addView(spacer)
                gridContainer.addView(line)
            }
            gridContainer.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            })
            chartContainer.addView(gridContainer, 0)

            // ─── 柱状图 ───
            val totalBowel = dailyData.values.sumOf { it.first }
            val totalPee = dailyData.values.sumOf { it.second }

            for ((date, data) in dailyData) {
                val (bowel, pee) = data
                val col = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setPadding(1, 0, 1, 0)
                }

                // 排便柱 (棕色) — 放在下面
                val barBowel = View(requireContext()).apply {
                    val h = if (maxVal > 0) (bowel.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(0xFF795548.toInt())
                        cornerRadius = 4f * density
                    }
                }
                // 排尿柱 (蓝色) — 放在上面
                val barPee = View(requireContext()).apply {
                    val h = if (maxVal > 0) (pee.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(0xFF1976D2.toInt())
                        cornerRadius = 4f * density
                    }
                }

                // 数值标签
                if (bowel > 0) {
                    val labelBowel = TextView(requireContext()).apply {
                        text = bowel.toString()
                        textSize = 8f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(0xFF795548.toInt())
                    }
                    col.addView(labelBowel)
                }
                if (pee > 0) {
                    val labelPee = TextView(requireContext()).apply {
                        text = pee.toString()
                        textSize = 8f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(0xFF1976D2.toInt())
                    }
                    col.addView(labelPee)
                }

                col.addView(barBowel)
                col.addView(barPee)

                // 日期标签
                val showLabel = if (chartDays > 15) {
                    date.endsWith("/01") || date.endsWith("/15") ||
                            date == dailyData.keys.first() || date == dailyData.keys.last()
                } else true
                val dateLabel = TextView(requireContext()).apply {
                    text = if (showLabel) date else ""
                    textSize = 7f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xAAFFFFFF.toInt())
                }
                col.addView(dateLabel)

                columnsContainer.addView(col)
            }

            // ─── 更新图例 ───
            binding.tvChartLegend.text = "● 排便 $totalBowel 次    ■ 排尿 $totalPee 次"

            // ─── 进场动画 ───
            columnsContainer.post {
                for (i in 0 until columnsContainer.childCount) {
                    val col = columnsContainer.getChildAt(i) as? LinearLayout ?: continue
                    for (j in 0 until col.childCount) {
                        val v = col.getChildAt(j)
                        if (v.height > 0) {
                            v.pivotY = v.height.toFloat()
                            v.scaleY = 0f
                            v.animate().scaleY(1f).setDuration(300).startDelay = (i * 30).toLong()
                        }
                    }
                }
            }
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