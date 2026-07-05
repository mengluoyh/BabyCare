// BabyCare/app/src/main/java/com/babycare/ui/WeightTrendFragment.kt
package com.babycare.ui

import android.graphics.*
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.WeightRecord
import com.babycare.util.Constants
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** 优化版体重趋势图 — 直接记录 + 高质量折线图 + 历史列表导出 */
class WeightTrendFragment : Fragment() {

    companion object {
        private const val KG_UNIT = "kg"
    }

    private var weightRecords = listOf<WeightRecord>()
    private val weightDao by lazy { (requireActivity().application as BabyCareApp).database.weightDao() }

    // 视图引用
    private lateinit var statsView: android.widget.TextView
    private lateinit var chartView: WeightTrendChart
    private lateinit var emptyText: android.widget.TextView
    private lateinit var etWeight: android.widget.EditText
    private lateinit var btnAddWeight: com.google.android.material.button.MaterialButton
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var recordAdapter: WeightListAdapter
    private lateinit var btnExport: com.google.android.material.button.MaterialButton

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return object : android.widget.ScrollView(requireContext()) {
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                super.onLayout(changed, l, t, r, b)
                if (childCount > 0) chartView.updateData(weightRecords)
            }
        }.apply {
            isFillViewport = true
            setBackgroundColor(android.graphics.Color.parseColor("#FFF0F4F8"))

            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL

                // ── 标题 ──
                val title = android.widget.TextView(context).apply {
                    text = "⚖️ 体重趋势图"
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(20, 20, 20, 4)
                }
                addView(title)

                // ── 快速记录体重输入 ──
                addView(android.widget.LinearLayout(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(16, 8, 16, 8) }
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL

                    etWeight = android.widget.EditText(context).apply {
                        hint = "输入体重 (kg)"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                        )
                        setTextColor(android.graphics.Color.parseColor("#FF333333"))
                        background = null
                    }
                    addView(etWeight)

                    btnAddWeight = com.google.android.material.button.MaterialButton(context).apply {
                        text = "➕ 记录"
                        setOnClickListener { saveWeight() }
                    }
                    addView(btnAddWeight)
                })

                // ── 统计摘要 ──
                statsView = android.widget.TextView(context).apply {
                    text = "暂无数据"
                    textSize = 14f
                    setPadding(20, 4, 20, 8)
                }
                addView(statsView)

                // ── 图表 ──
                chartView = WeightTrendChart(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 440
                    ).apply { setMargins(16, 4, 16, 8) }
                }
                addView(chartView)

                // ── 空状态 ──
                emptyText = android.widget.TextView(context).apply {
                    text = "暂无体重记录\n请在输入框中输入体重并点击「记录」"
                    textSize = 15f
                    gravity = android.view.Gravity.CENTER
                    setPadding(32, 48, 32, 48)
                    visibility = View.GONE
                }
                addView(emptyText)

                // ── 记录列表 ──
                val listTitle = android.widget.TextView(context).apply {
                    text = "📋 历史记录"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(20, 12, 20, 4)
                }
                addView(listTitle)

                recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(context)
                }
                addView(recyclerView)

                // ── 导出按钮 ──
                btnExport = com.google.android.material.button.MaterialButton(context).apply {
                    text = "📤 导出体重记录"
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(16, 8, 16, 20) }
                    setOnClickListener { exportWeightRecords() }
                }
                addView(btnExport)
            }
            addView(container)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recordAdapter = WeightListAdapter(onDelete = { deleteWeight(it) })
        recyclerView.adapter = recordAdapter
        loadWeightRecords()
    }

    override fun onResume() {
        super.onResume()
        loadWeightRecords()
    }

    // ── 保存体重 ──
    private fun saveWeight() {
        val text = etWeight.text.toString()
        val w = text.toFloatOrNull()
        if (w == null || w <= 0f || w > 200f) {
            etWeight.error = "请输入有效体重（0~200kg）"
            return
        }
        lifecycleScope.launch {
            weightDao.insert(WeightRecord(weight = w))
            etWeight.text?.clear()
            Toast.makeText(requireContext(), "✅ 已记录 ${w} kg", Toast.LENGTH_SHORT).show()
            loadWeightRecords()
        }
    }

    // ── 删除体重 ──
    private fun deleteWeight(record: WeightRecord) {
        lifecycleScope.launch {
            weightDao.delete(record)
            loadWeightRecords()
        }
    }

    // ── 加载数据 ──
    private fun loadWeightRecords() {
        lifecycleScope.launch {
            weightRecords = weightDao.getAll()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            if (weightRecords.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                chartView.visibility = View.GONE
                statsView.text = ""
                recordAdapter.submitList(emptyList())
                return@launch
            }

            emptyText.visibility = View.GONE
            chartView.visibility = View.VISIBLE
            chartView.updateData(weightRecords)

            val latest = weightRecords.last()
            val first = weightRecords.first()
            val diff = latest.weight - first.weight
            val minW = weightRecords.minOf { it.weight }
            val maxW = weightRecords.maxOf { it.weight }
            val period = if (weightRecords.size > 1) {
                "%.1f 天".format((weightRecords.last().timestamp - weightRecords.first().timestamp) / 86400000f)
            } else "首次"

            statsView.text = buildString {
                append("📊 共${weightRecords.size}次  ")
                append("当前${latest.weight}kg  ")
                append("变化${if (diff >= 0) "+" else ""}%.2fkg  ".format(diff))
                append("最低${minW}kg 最高${maxW}kg  ")
                append("跨度$period")
            }

            recordAdapter.submitList(weightRecords.reversed())
        }
    }

    private fun exportWeightRecords() {
        lifecycleScope.launch {
            if (weightRecords.isEmpty()) {
                Toast.makeText(requireContext(), "没有体重记录可导出", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("========== 体重记录导出 ==========")
            sb.appendLine("导出时间：${sdf.format(Date())}")
            sb.appendLine("共 ${weightRecords.size} 条记录")
            sb.appendLine()
            weightRecords.forEachIndexed { i, r ->
                sb.appendLine("${i + 1}. ${sdf.format(Date(r.timestamp))}  ⚖️ ${r.weight} kg")
            }
            sb.appendLine("\n========== 导出结束 ==========")

            try {
                val dir = File(Constants.EXPORT_DIR)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "weight_records_${System.currentTimeMillis()}.txt")
                file.writeText(sb.toString())
                Toast.makeText(requireContext(), "导出成功：${file.absolutePath}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ════════════════════════════════════════════════
//  优化版体重折线图（Canvas绘制）
// ════════════════════════════════════════════════

class WeightTrendChart(context: android.content.Context) : View(context) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1565C0.toInt()
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isDither = true
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1565C0.toInt()
        style = Paint.Style.FILL
    }
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1A000000.toInt()
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = 26f
    }
    private val gradientColors = intArrayOf(0x301565C0.toInt(), 0x001565C0.toInt())
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1A000000.toInt()
        setShadowLayer(6f, 0f, 2f, 0x33000000.toInt())
    }

    private val cornerPath = Path()
    private var records = listOf<WeightRecord>()

    fun updateData(data: List<WeightRecord>) {
        records = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, 16f, 16f, bgPaint)

        if (records.isEmpty()) return
        if (records.size == 1) {
            val cx = w / 2f; val cy = h / 2f
            canvas.drawCircle(cx, cy, 10f, shadowPaint)
            canvas.drawCircle(cx, cy, 10f, pointPaint)
            canvas.drawCircle(cx, cy, 10f, pointStrokePaint)
            labelPaint.textSize = 38f; labelPaint.color = 0xFF1565C0.toInt()
            canvas.drawText("%.1f kg".format(records[0].weight), cx - 60f, cy + 50f, labelPaint)
            return
        }

        val padL = 56f; val padR = 24f; val padT = 24f; val padB = 48f
        val chartW = w - padL - padR
        val chartH = h - padT - padB
        val minW = records.minOf { it.weight }
        val maxW = records.maxOf { it.weight }
        val range = (maxW - minW).coerceAtLeast(0.5f)

        // 网格线 + Y轴标签
        for (i in 0..4) {
            val y = padT + chartH * (1 - i / 4f)
            canvas.drawLine(padL, y, w - padR, y, gridPaint)
            val v = minW + range * i / 4f
            labelPaint.color = 0xFF888888.toInt(); labelPaint.textSize = 24f
            canvas.drawText("%.1f".format(v), 4f, y + 8f, labelPaint)
        }

        // 绘制点坐标
        val points = records.mapIndexed { idx, r ->
            val x = padL + chartW * idx / (records.size - 1).coerceAtLeast(1)
            val y = padT + chartH * (1 - (r.weight - minW) / range)
            Pair(x, y)
        }

        // 渐变填充
        val fillPath = Path()
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) { fillPath.moveTo(x, padT + chartH); fillPath.lineTo(x, y) }
            else fillPath.lineTo(x, y)
        }
        fillPath.lineTo(points.last().first, padT + chartH)
        fillPath.close()
        gradientPaint.shader = LinearGradient(0f, padT, 0f, padT + chartH, gradientColors[0], gradientColors[1], Shader.TileMode.CLAMP)
        canvas.drawPath(fillPath, gradientPaint)

        // 折线
        val linePath = Path()
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        canvas.drawPath(linePath, linePaint)

        // 数据点
        points.forEach { (x, y) ->
            canvas.drawCircle(x, y, 7f, shadowPaint)
            canvas.drawCircle(x, y, 7f, pointPaint)
            canvas.drawCircle(x, y, 7f, pointStrokePaint)
        }

        // 最后一个点（最新）上方显示数值
        val last = points.last()
        labelPaint.textSize = 30f; labelPaint.color = 0xFF1565C0.toInt()
        labelPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("%.1f".format(records.last().weight), last.first - 24f, last.second - 18f, labelPaint)
        labelPaint.typeface = Typeface.DEFAULT

        // X轴标签
        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val step = (records.size / 6).coerceAtLeast(1)
        labelPaint.textSize = 24f; labelPaint.color = 0xFF888888.toInt()
        for (i in records.indices step step) {
            val x = padL + chartW * i / (records.size - 1).coerceAtLeast(1)
            canvas.drawText(sdf.format(Date(records[i].timestamp)), x - 28f, h - 10f, labelPaint)
        }
    }
}

// ════════════════════════════════════════════════
//  体重记录列表适配器
// ════════════════════════════════════════════════

class WeightListAdapter(
    private val onDelete: (WeightRecord) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<WeightListAdapter.VH>() {

    private var records = emptyList<WeightRecord>()

    fun submitList(list: List<WeightRecord>) {
        records = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = records.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = android.widget.TextView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(16, 3, 16, 3) }
            textSize = 14f
            setPadding(12, 10, 12, 10)
            setBackgroundColor(0x0A000000.toInt())
        }
        return VH(tv) { position -> onDelete(records[position]) }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = records[position]
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val timeStr = sdf.format(Date(r.timestamp))
        holder.tv.text = "$timeStr    ⚖️ ${r.weight} kg"
    }

    inner class VH(val tv: android.widget.TextView, onDelete: (Int) -> Unit) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(tv) {
        init { tv.setOnLongClickListener { onDelete(adapterPosition); true } }
    }
}