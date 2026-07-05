// BabyCare/app/src/main/java/com/babycare/ui/FeedingRecordsFragment.kt
package com.babycare.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.databinding.FragmentFeedingRecordsBinding
import com.babycare.util.AgeCalculator
import com.babycare.util.ChartDrawer
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
                val cal = Calendar.getInstance().apply {
                    timeInMillis = end
                    add(Calendar.DAY_OF_MONTH, -i)
                }
                dailyData[sdf.format(cal.time)] = Pair(0, 0)
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

            val totalBreast = dailyData.values.sumOf { it.first }
            val totalFormula = dailyData.values.sumOf { it.second }

            ChartDrawer.draw(ChartDrawer.ChartConfig(
                context = requireContext(),
                chartContainer = binding.layoutChart,
                columnsContainer = binding.chartColumns,
                dailyData = dailyData,
                chartDays = chartDays,
                density = resources.displayMetrics.density,
                barColor1 = 0xFFE65100.toInt(),   // 配方奶（底部，橙色）
                barColor2 = 0xFF1976D2.toInt(),   // 母乳（顶部，蓝色）
                labelColor1 = 0xFFE65100.toInt(),
                labelColor2 = 0xFF1976D2.toInt(),
                legendFormat = "● 母乳 %d 次    ■ 配方奶 %d ml",
                total1 = totalBreast,
                total2 = totalFormula
            ))

            binding.tvChartLegend.text = "● 母乳 $totalBreast 次    ■ 配方奶 $totalFormula ml"
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
            val records = feedingDao.getAllSnapshot()
            val file = ExportUtil.exportToJson(
                requireContext(),
                records,
                "feeding_${System.currentTimeMillis()}.json"
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