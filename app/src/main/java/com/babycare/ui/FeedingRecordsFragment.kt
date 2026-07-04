// BabyCare/app/src/main/java/com/babycare/ui/FeedingRecordsFragment.kt
package com.babycare.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.databinding.FragmentFeedingRecordsBinding
import com.babycare.util.AgeCalculator
import com.babycare.util.ExportUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FeedingRecordsFragment : Fragment() {
    private var _binding: FragmentFeedingRecordsBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy {
        (requireActivity().application as BabyCareApp).database.feedingDao()
    }
    private val adapter = FeedingRecordAdapter { record -> deleteRecord(record) }
    private var chartDays = 7

    // 文件选择器——导入
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importRecords(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedingRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnExport.setOnClickListener { exportRecords() }
        binding.btnImport.setOnClickListener { importLauncher.launch("application/json") }

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
            feedingDao.getAll().collect { records ->
                adapter.submitList(records)
                binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        refreshStats()
        drawChart()
    }

    private fun refreshStats() {
        lifecycleScope.launch {
            val (start, end) = AgeCalculator.getTodayRange()
            val breast = feedingDao.getBreastCountBetween(start, end)
            val formula = feedingDao.getFormulaTotalBetween(start, end)
            binding.tvTodayBreastCount.text = breast.toString()
            binding.tvTodayFormulaAmount.text = formula.toString()
        }
    }

    private fun drawChart() {
        lifecycleScope.launch {
            val end = System.currentTimeMillis()
            val start = AgeCalculator.getPastDaysStart(chartDays - 1)
            val feedings = feedingDao.getFeedingsBetween(start, end)

            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            val dailyData = mutableMapOf<String, Pair<Int, Int>>()
            for (i in (chartDays - 1) downTo 0) {
                val day = Date(end - i * 24 * 60 * 60 * 1000L)
                dailyData[sdf.format(day)] = Pair(0, 0)
            }
            for (f in feedings) {
                val key = sdf.format(Date(f.timestamp))
                val (breast, formula) = dailyData[key] ?: Pair(0, 0)
                if (f.feedType == "breast") {
                    dailyData[key] = Pair(breast + 1, formula)
                } else {
                    dailyData[key] = Pair(breast, formula + (f.volume ?: 0))
                }
            }

            binding.layoutChart.removeAllViews()
            val maxVal = dailyData.values.maxOfOrNull { maxOf(it.first, it.second) } ?: 1
            val density = resources.displayMetrics.density
            val barWidth = (Math.max(8, 24 - chartDays)).toInt() * density.toInt()
            val chartHeight = binding.layoutChart.height.coerceAtLeast(100)

            for ((date, data) in dailyData) {
                val (breast, formula) = data
                val col = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setPadding(1, 0, 1, 0)
                }

                // 配方奶柱 (橙色 #E65100)
                val barF = View(requireContext()).apply {
                    val h = if (maxVal > 0) (formula.toFloat() / maxVal * chartHeight * 0.6f).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    setBackgroundColor(0xFFE65100.toInt())
                }
                // 母乳柱 (蓝色 #1976D2)
                val barB = View(requireContext()).apply {
                    val h = if (maxVal > 0) (breast.toFloat() / maxVal * chartHeight * 0.6f).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    setBackgroundColor(0xFF1976D2.toInt())
                }

                val dateLabel = TextView(requireContext()).apply {
                    val showLabel = if (chartDays > 15) {
                        date.endsWith("/01") || date.endsWith("/15") || date == dailyData.keys.first() || date == dailyData.keys.last()
                    } else true
                    text = if (showLabel) date else ""
                    textSize = 8f
                    gravity = android.view.Gravity.CENTER
                }

                col.addView(barF)
                col.addView(barB)
                col.addView(dateLabel)
                binding.layoutChart.addView(col)
            }
        }
    }

    private fun deleteRecord(record: FeedingRecord) {
        lifecycleScope.launch {
            feedingDao.delete(record)
            refreshStats()
            drawChart()
        }
    }

    private fun exportRecords() {
        lifecycleScope.launch {
            val records = feedingDao.getAll().let {
                var list = emptyList<FeedingRecord>()
                it.collect { list = it }
                list
            }
            val file = ExportUtil.exportToJson(
                requireContext(),
                records,
                "feeding_${System.currentTimeMillis()}.json"
            )
            if (file != null) {
                Toast.makeText(requireContext(), "导出成功：${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importRecords(uri: Uri) {
        lifecycleScope.launch {
            val records = ExportUtil.importFromJson<FeedingRecord>(requireContext(), uri)
            if (records.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "导入失败：文件为空或格式错误", Toast.LENGTH_SHORT).show()
                return@launch
            }
            feedingDao.insertAll(records)
            Toast.makeText(requireContext(), "导入成功：${records.size} 条记录", Toast.LENGTH_SHORT).show()
            refreshStats()
            drawChart()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}