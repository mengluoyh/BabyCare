// BabyCare/app/src/main/java/com/babycare/ui/WeightTrendFragment.kt
package com.babycare.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.WeightRecord
import com.babycare.util.Constants
import com.babycare.util.ExportUtil
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class WeightTrendFragment : Fragment() {

    private var weightRecords = listOf<WeightRecord>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return object : android.widget.ScrollView(requireContext()) {
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                super.onLayout(changed, l, t, r, b)
                if (childCount > 0) {
                    val chart = getChildAt(0) as? WeightChartView
                    chart?.updateData(weightRecords)
                }
            }
        }.apply {
            isFillViewport = true
            setBackgroundColor(android.graphics.Color.parseColor("#FFF5F5F5"))

            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL

                // 标题
                val title = android.widget.TextView(context).apply {
                    text = "⚖️ 体重趋势图"
                    textSize = 20f
                    setPadding(16, 16, 16, 8)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                addView(title)

                // 统计数据
                val statsText = android.widget.TextView(context).apply {
                    id = View.generateViewId()
                    text = "加载中..."
                    textSize = 14f
                    setPadding(16, 4, 16, 8)
                }
                addView(statsText)
                this@WeightTrendFragment.statsView = statsText

                // 图表
                val chartView = WeightChartView(context).apply {
                    id = View.generateViewId()
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 400
                ).apply { setMargins(16, 8, 16, 8) }
                }
                addView(chartView)
                this@WeightTrendFragment.chartView = chartView

                // 空状态
                val emptyText = android.widget.TextView(context).apply {
                    id = View.generateViewId()
                    text = "暂无体重记录，请在「宝宝成长」中记录体重"
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setPadding(32, 32, 32, 32)
                    visibility = View.GONE
                }
                addView(emptyText)
                this@WeightTrendFragment.emptyText = emptyText

                // 导出按钮
                val exportBtn = com.google.android.material.button.MaterialButton(context).apply {
                    text = "📤 导出体重记录"
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 8, 16, 16) }
                    setOnClickListener { exportWeightRecords() }
                }
                addView(exportBtn)
            }
            addView(container)
        }
    }

    private lateinit var statsView: android.widget.TextView
    private lateinit var chartView: WeightChartView
    private lateinit var emptyText: android.widget.TextView

    override fun onResume() {
        super.onResume()
        loadWeightRecords()
    }

    private fun loadWeightRecords() {
        lifecycleScope.launch {
            val dao = (requireActivity().application as BabyCareApp).database.weightDao()
            weightRecords = dao.getAll()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            if (weightRecords.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                chartView.visibility = View.GONE
                statsView.text = ""
                return@launch
            }

            emptyText.visibility = View.GONE
            chartView.visibility = View.VISIBLE
            chartView.updateData(weightRecords)

            val latest = weightRecords.last()
            val first = weightRecords.first()
            val diff = latest.weight - first.weight
            val period = if (weightRecords.size > 1) {
                val days = ((weightRecords.last().timestamp - weightRecords.first().timestamp) / 86400000f)
                "%.1f 天".format(days)
            } else "首次记录"

            statsView.text = "📊 共 ${weightRecords.size} 次记录 | 当前 ${latest.weight} kg | 变化 ${if (diff >= 0) "+" else ""}%.2f kg | 跨度 $period".format(diff)
        }
    }

    private fun exportWeightRecords() {
        lifecycleScope.launch {
            val records = weightRecords
            if (records.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "没有体重记录可导出", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("========== 体重记录导出 ==========")
            sb.appendLine("导出时间：${sdf.format(Date())}")
            sb.appendLine("共 ${records.size} 条记录")
            sb.appendLine()
            records.forEachIndexed { i, r ->
                val timeStr = sdf.format(Date(r.timestamp))
                sb.appendLine("${i + 1}. $timeStr  ⚖️ ${r.weight} kg")
            }
            sb.appendLine()
            sb.appendLine("========== 导出结束 ==========")

            try {
                val dir = File(Constants.EXPORT_DIR)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "weight_records_${System.currentTimeMillis()}.txt")
                file.writeText(sb.toString())
                android.widget.Toast.makeText(requireContext(), "导出成功：${file.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "导出失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 简单的体重折线图View */
class WeightChartView(context: android.content.Context) : View(context) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1976D2.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1976D2.toInt()
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000.toInt()
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = 28f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x181976D2.toInt()
        style = Paint.Style.FILL
    }

    private var records = listOf<WeightRecord>()

    fun updateData(data: List<WeightRecord>) {
        records = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (records.size < 2) {
            if (records.size == 1) {
                // 画单个点
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, 8f, pointPaint)
                canvas.drawText("%.1f kg".format(records[0].weight), cx - 40f, cy + 40f, textPaint)
            }
            return
        }

        val padding = 50f
        val chartW = width - padding * 2
        val chartH = height - padding * 2
        val minW = records.minOf { it.weight }
        val maxW = records.maxOf { it.weight }
        val range = (maxW - minW).coerceAtLeast(0.5f)

        // 网格
        for (i in 0..4) {
            val y = padding + chartH * (1 - i / 4f)
            canvas.drawLine(padding, y, width - padding, y, gridPaint)
            val v = minW + range * i / 4f
            canvas.drawText("%.1f".format(v), 4f, y + 10f, textPaint)
        }

        // 折线 + 填充
        val path = Path()
        val fillPath = Path()
        val points = records.mapIndexed { index, r ->
            val x = padding + chartW * index / (records.size - 1).coerceAtLeast(1)
            val y = padding + chartH * (1 - (r.weight - minW) / range)
            Pair(x, y)
        }

        points.forEachIndexed { i, (x, y) ->
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, padding + chartH)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            canvas.drawCircle(x, y, 6f, pointPaint)
        }
        fillPath.lineTo(points.last().first, padding + chartH)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        // X轴标签（显示日期）
        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val step = (records.size / 5).coerceAtLeast(1)
        for (i in records.indices step step) {
            val x = padding + chartW * i / (records.size - 1).coerceAtLeast(1)
            canvas.drawText(sdf.format(Date(records[i].timestamp)), x - 20f, height - 8f, textPaint)
        }
    }
}