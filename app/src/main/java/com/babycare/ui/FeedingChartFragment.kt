// BabyCare/app/src/main/java/com/babycare/ui/FeedingChartFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.databinding.FragmentFeedingChartBinding
import com.babycare.util.AgeCalculator
import com.babycare.util.ChartDrawer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FeedingChartFragment : Fragment() {
    private var _binding: FragmentFeedingChartBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy { (requireActivity().application as BabyCareApp).database.feedingDao() }
    private var chartDays = 7

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedingChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rgChartDays.setOnCheckedChangeListener { _, checkedId ->
            chartDays = when (checkedId) {
                com.babycare.R.id.rbChart15d -> 15
                com.babycare.R.id.rbChart30d -> 30
                else -> 7
            }
            drawChart()
        }

        drawChart()
    }

    private fun drawChart() {
        lifecycleScope.launch {
            val end = System.currentTimeMillis()
            val start = AgeCalculator.getPastDaysStart(chartDays - 1)
            val feedings = feedingDao.getFeedingsBetween(start, end)

            val sdf = CHART_FMT
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
                barColor1 = android.graphics.Color.parseColor("#1976D2"),
                barColor2 = android.graphics.Color.parseColor("#E65100"),
                labelColor1 = android.graphics.Color.parseColor("#1976D2"),
                labelColor2 = android.graphics.Color.parseColor("#E65100"),
                legendFormat = "● 母乳 %d 次    ■ 配方奶 %d ml",
                total1 = totalBreast,
                total2 = totalFormula,
                sideBySide = true
            ))

            binding.tvChartLegend.text = "● 母乳 $totalBreast 次    ■ 配方奶 $totalFormula ml"
        }
    }

    companion object {
        private val CHART_FMT = SimpleDateFormat("MM/dd", Locale.getDefault())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
