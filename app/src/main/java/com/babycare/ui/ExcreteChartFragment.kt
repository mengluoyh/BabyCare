// BabyCare/app/src/main/java/com/babycare/ui/ExcreteChartFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.databinding.FragmentExcreteChartBinding
import com.babycare.util.AgeCalculator
import com.babycare.util.ChartDrawer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ExcreteChartFragment : Fragment() {
    private var _binding: FragmentExcreteChartBinding? = null
    private val binding get() = _binding!!
    private val excreteDao by lazy { (requireActivity().application as BabyCareApp).database.excreteDao() }
    private var chartDays = 7

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExcreteChartBinding.inflate(inflater, container, false)
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
            val records = excreteDao.getExcretesBetween(start, end)

            val sdf = CHART_FMT
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
                if (r.type == "bowel") dailyData[key] = Pair(bowel + 1, pee)
                else dailyData[key] = Pair(bowel, pee + 1)
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
                barColor1 = android.graphics.Color.parseColor("#795548"),
                barColor2 = android.graphics.Color.parseColor("#1976D2"),
                labelColor1 = android.graphics.Color.parseColor("#795548"),
                labelColor2 = android.graphics.Color.parseColor("#1976D2"),
                legendFormat = "● 排便 %d 次    ■ 排尿 %d 次",
                total1 = totalBowel,
                total2 = totalPee,
                sideBySide = true
            ))

            binding.tvChartLegend.text = "● 排便 $totalBowel 次    ■ 排尿 $totalPee 次"
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