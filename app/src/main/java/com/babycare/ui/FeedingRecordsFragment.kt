// BabyCare/app/src/main/java/com/babycare/ui/FeedingRecordsFragment.kt
package com.babycare.ui

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

            val chartContainer = binding.layoutChart
            val columnsContainer = binding.chartColumns
            columnsContainer.removeAllViews()
            chartContainer.removeAllViews()
            chartContainer.addView(columnsContainer)

            val maxVal = dailyData.values.maxOfOrNull { maxOf(it.first, it.second) } ?: 1
            val density = resources.displayMetrics.density
            val barWidth = (Math.max(8, 24 - chartDays)).toInt() * density.toInt()
            val chartHeight = chartContainer.height.coerceAtLeast(100)
            val barAreaHeight = (chartHeight * 0.75f).toInt() // 柱状图占75%高度，顶部留标签空间

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
            // 最后一个spacer填满底部
            gridContainer.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            })
            chartContainer.addView(gridContainer, 0)

            // ─── 柱状图 ───
            val totalBreast = dailyData.values.sumOf { it.first }
            val totalFormula = dailyData.values.sumOf { it.second }

            for ((date, data) in dailyData) {
                val (breast, formula) = data
                val col = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setPadding(1, 0, 1, 0)
                }

                // 配方奶柱 (橙色) — 放在下面
                val barF = View(requireContext()).apply {
                    val h = if (maxVal > 0) (formula.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(0xFFE65100.toInt())
                        cornerRadius = 4f * density
                    }
                }
                // 母乳柱 (蓝色) — 放在上面
                val barB = View(requireContext()).apply {
                    val h = if (maxVal > 0) (breast.toFloat() / maxVal * barAreaHeight).toInt() else 0
                    layoutParams = LinearLayout.LayoutParams(barWidth.coerceAtLeast(4), h.coerceAtLeast(2))
                    background = GradientDrawable().apply {
                        setColor(0xFF1976D2.toInt())
                        cornerRadius = 4f * density
                    }
                }

                // 数值标签
                if (formula > 0) {
                    val labelF = TextView(requireContext()).apply {
                        text = formula.toString()
                        textSize = 8f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(0xFFE65100.toInt())
                    }
                    col.addView(labelF)
                }
                if (breast > 0) {
                    val labelB = TextView(requireContext()).apply {
                        text = breast.toString()
                        textSize = 8f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(0xFF1976D2.toInt())
                    }
                    col.addView(labelB)
                }

                col.addView(barF)
                col.addView(barB)

                // 日期标签
                val showLabel = if (chartDays > 15) {
                    date.endsWith("/01") || date.endsWith("/15") ||
                            date == dailyData.keys.first() || date == dailyData.keys.last()
                } else true
                val dateLabel = TextView(requireContext()).apply {
                    text = if (showLabel) date else ""
                    textSize = 7f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFF000000.toInt())
                }
                col.addView(dateLabel)

                columnsContainer.addView(col)
            }

            // ─── 更新图例 ───
            binding.tvChartLegend.text = "● 母乳 $totalBreast 次    ■ 配方奶 $totalFormula ml"

            // ─── 进场动画：柱子从底部升起 ───
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