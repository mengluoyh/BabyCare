// BabyCare/app/src/main/java/com/babycare/ui/WeightTrendFragment.kt
package com.babycare.ui

import android.app.AlertDialog
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

/** 体重记录 — 输入体重保存，记录当前日期、时间 */
class WeightTrendFragment : Fragment() {

    private var weightRecords = listOf<WeightRecord>()
    private val weightDao by lazy { (requireActivity().application as BabyCareApp).database.weightDao() }

    private lateinit var etWeight: android.widget.EditText
    private lateinit var btnSave: com.google.android.material.button.MaterialButton
    private lateinit var tvStats: android.widget.TextView
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: RecordListAdapter
    private lateinit var btnExport: com.google.android.material.button.MaterialButton
    private lateinit var emptyText: android.widget.TextView

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return object : android.widget.ScrollView(requireContext()) {
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
        }.apply {
            isFillViewport = true
            setBackgroundColor(requireContext().getColor(com.babycare.R.color.background))

            addView(android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL

                // ── 标题 ──
                addView(android.widget.TextView(context).apply {
                    text = "⚖️ 体重记录"
                    textSize = 22f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(20, 20, 20, 4)
                })

                // ── 输入行 ──
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
                        setTextColor(requireContext().getColor(com.babycare.R.color.on_background))
                        background = null
                    }
                    addView(etWeight)

                    btnSave = com.google.android.material.button.MaterialButton(context).apply {
                        text = "💾 保存"
                        setOnClickListener { saveWeight() }
                    }
                    addView(btnSave)
                })

                // ── 统计 ──
                tvStats = android.widget.TextView(context).apply {
                    text = "暂无记录"
                    textSize = 14f
                    setPadding(20, 4, 20, 8)
                }
                addView(tvStats)

                // ── 空状态 ──
                emptyText = android.widget.TextView(context).apply {
                    text = "暂无体重记录"
                    gravity = android.view.Gravity.CENTER
                    textSize = 15f
                    setPadding(32, 48, 32, 48)
                    visibility = View.GONE
                }
                addView(emptyText)

                // ── 历史记录 ──
                addView(android.widget.TextView(context).apply {
                    text = "📋 历史记录"
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(20, 12, 20, 4)
                })

                recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(context)
                }
                addView(recyclerView)

                // ── 导出 ──
                btnExport = com.google.android.material.button.MaterialButton(context).apply {
                    text = "📤 导出体重记录"
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(16, 8, 16, 20) }
                    setOnClickListener { exportWeightRecords() }
                }
                addView(btnExport)
            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RecordListAdapter { deleteWeight(it) }
        recyclerView.adapter = adapter
        loadRecords()
    }

    override fun onResume() {
        super.onResume()
        loadRecords()
    }

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
            loadRecords()
        }
    }

    private fun deleteWeight(record: WeightRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除确认")
            .setMessage("确定删除此条体重记录？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    weightDao.delete(record)
                    loadRecords()
                }
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            weightRecords = weightDao.getAll()
            if (weightRecords.isEmpty()) {
                tvStats.text = "暂无记录"
                emptyText.visibility = View.VISIBLE
                adapter.submitList(emptyList())
                return@launch
            }
            emptyText.visibility = View.GONE
            val latest = weightRecords.last()
            tvStats.text = "📊 共 ${weightRecords.size} 次记录 | 当前 ${latest.weight} kg"
            adapter.submitList(weightRecords.reversed())
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

    /** 体重记录列表适配器 */
    inner class RecordListAdapter(
        private val onDelete: (WeightRecord) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<RecordListAdapter.VH>() {
        private var records = emptyList<WeightRecord>()
        fun submitList(list: List<WeightRecord>) { records = list; notifyDataSetChanged() }
        override fun getItemCount() = records.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = android.widget.TextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(16, 3, 16, 3) }
                textSize = 14f
                setPadding(12, 10, 12, 10)
                setBackgroundColor(0x0A000000.toInt())
                setOnLongClickListener {
                    val pos = getTag() as? Int ?: return@setOnLongClickListener true
                    if (pos < records.size) onDelete(records[pos])
                    true
                }
            }
            return VH(tv)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = records[position]
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.tv.text = "${sdf.format(Date(r.timestamp))}    ⚖️ ${r.weight} kg"
            holder.tv.setTag(position)
        }
        inner class VH(val tv: android.widget.TextView) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(tv)
    }
}