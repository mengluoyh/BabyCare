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
import com.babycare.util.Constants
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
            val dailyData = mutableMapOf<String, Triple<Int, Int, Int>>()
            for (i in (chartDays - 1) downTo 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = end
                    add(Calendar.DAY_OF_MONTH, -i)
                }
                dailyData[sdf.format(cal.time)] = Triple(0, 0, 0)
            }
            for (f in feedings) {
                val key = sdf.format(Date(f.timestamp))
                val (breast, formula, bottleBreast) = dailyData[key] ?: Triple(0, 0, 0)
                when (f.feedType) {
                    Constants.FEED_BREAST -> dailyData[key] = Triple(breast + 1, formula, bottleBreast)
                    Constants.FEED_BOTTLE_BREAST -> dailyData[key] = Triple(breast, formula, bottleBreast + (f.volume ?: 0))
                    else -> dailyData[key] = Triple(breast, formula + (f.volume ?: 0), bottleBreast)
                }
            }

            val totalBreast = dailyData.values.sumOf { it.first }
            val totalFormula = dailyData.values.sumOf { it.second }
            val totalBottleBreast = dailyData.values.sumOf { it.third }

            ChartDrawer.draw(ChartDrawer.ChartConfig(
                context = requireContext(),
                chartContainer = binding.layoutChart,
                columnsContainer = binding.chartColumns,
                dailyData = dailyData,
                chartDays = chartDays,
                density = resources.displayMetrics.density,
                barColor1 = android.graphics.Color.parseColor("#1976D2"),
                barColor2 = android.graphics.Color.parseColor("#E65100"),
                barColor3 = android.graphics.Color.parseColor("#388E3C"),
                labelColor1 = android.graphics.Color.parseColor("#1976D2"),
                labelColor2 = android.graphics.Color.parseColor("#E65100"),
                labelColor3 = android.graphics.Color.parseColor("#388E3C"),
                legendFormat = "● 亲喂 %d 次    ■ 配方奶 %d ml    ▲ 瓶喂母乳 %d ml",
                total1 = totalBreast,
                total2 = totalFormula,
                total3 = totalBottleBreast,
                sideBySide = true
            ))

            binding.tvChartLegend.text = "● 亲喂 $totalBreast 次    ■ 配方奶 $totalFormula ml    ▲ 瓶喂母乳 $totalBottleBreast ml"
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
