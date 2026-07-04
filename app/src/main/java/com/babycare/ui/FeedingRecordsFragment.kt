// BabyCare/app/src/main/java/com/example/babycare/ui/FeedingRecordsFragment.kt
package com.babycare.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedingRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnExport.setOnClickListener { exportRecords() }

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
            val start = AgeCalculator.getPastDaysStart(6)
            val feedings = feedingDao.getFeedingsBetween(start, end)

            // 按日期分组
            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            val dailyData = mutableMapOf<String, Pair<Int, Int>>()
            for (i in 6 downTo 0) {
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

            // 绘制柱状图
            binding.layoutChart.removeAllViews()
            val maxVal = dailyData.values.maxOfOrNull { maxOf(it.first, it.second) } ?: 1
            val density = resources.displayMetrics.density
            val barWidth = (24 * density).toInt()
            val chartHeight = binding.layoutChart.height.coerceAtLeast(100)

            for ((date, data) in dailyData) {
                val (breast, formula) = data
                val col = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setPadding(2, 0, 2, 0)
                }

                // 配方奶柱 (橙色)
                val barF = View(requireContext()).apply {
                    val h = if (maxVal > 0) (formula.toFloat() / maxVal * chartHeight * 0.6f).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth, h.coerceAtLeast(2))
                    setBackgroundColor(Color.parseColor("#E65100"))
                }
                // 母乳柱 (蓝色)
                val barB = View(requireContext()).apply {
                    val h = if (maxVal > 0) (breast.toFloat() / maxVal * chartHeight * 0.6f).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth, h.coerceAtLeast(2))
                    setBackgroundColor(Color.parseColor("#1976D2"))
                }

                val dateLabel = TextView(requireContext()).apply {
                    text = date
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                }

                col.addView(barF)
                col.addView(barB)
                col.addView(dateLabel)
                binding.layoutChart.addView(col)
            }
        }
    }

    private fun deleteRecord(record: com.babycare.data.FeedingRecord) {
        lifecycleScope.launch {
            feedingDao.delete(record)
            refreshStats()
            drawChart()
        }
    }

    private fun exportRecords() {
        lifecycleScope.launch {
            val records = feedingDao.getAll().let {
                var list = emptyList<com.babycare.data.FeedingRecord>()
                it.collect { list = it }
                list
            }
            val file = ExportUtil.exportToJson(
                requireContext(),
                records,
                "feeding_${System.currentTimeMillis()}.json"
            )
            if (file != null) {
                Toast.makeText(requireContext(), "导出成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}