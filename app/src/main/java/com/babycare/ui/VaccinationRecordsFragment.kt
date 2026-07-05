// BabyCare/app/src/main/java/com/babycare/ui/VaccinationRecordsFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.VaccinationRecord
import com.babycare.util.Constants
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VaccinationRecordsFragment : Fragment() {

    private var vaccineRecords = listOf<VaccinationRecord>()
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: VaccineListViewAdapter
    private lateinit var emptyText: android.widget.TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            setBackgroundColor(context.getColor(com.babycare.R.color.background))

            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL

                // 标题
                val title = android.widget.TextView(context).apply {
                    text = "💉 疫苗接种信息记录"
                    textSize = 20f
                    setPadding(16, 16, 16, 8)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                addView(title)

                // 空状态
                val empty = android.widget.TextView(context).apply {
                    id = View.generateViewId()
                    text = "暂无疫苗接种记录"
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setPadding(32, 32, 32, 32)
                    visibility = View.VISIBLE
                }
                addView(empty)
                this@VaccinationRecordsFragment.emptyText = empty

                // RecyclerView
                val rv = androidx.recyclerview.widget.RecyclerView(context).apply {
                    id = View.generateViewId()
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(context)
                }
                addView(rv)
                this@VaccinationRecordsFragment.recyclerView = rv

                // 导出按钮
                val exportBtn = com.google.android.material.button.MaterialButton(context).apply {
                    text = "📤 导出疫苗记录"
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(16, 8, 16, 16) }
                    setOnClickListener { exportVaccineRecords() }
                }
                addView(exportBtn)
            }
            addView(container)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = VaccineListViewAdapter()
        recyclerView.adapter = adapter
        loadVaccines()
    }

    override fun onResume() {
        super.onResume()
        loadVaccines()
    }

    private fun loadVaccines() {
        lifecycleScope.launch {
            val dao = (requireActivity().application as BabyCareApp).database.vaccineDao()
            vaccineRecords = dao.getAllSnapshot()
            adapter.submitList(vaccineRecords)
            emptyText.visibility = if (vaccineRecords.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (vaccineRecords.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun exportVaccineRecords() {
        lifecycleScope.launch {
            val records = vaccineRecords
            if (records.isEmpty()) {
                Toast.makeText(requireContext(), "没有疫苗记录可导出", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("========== 疫苗接种记录导出 ==========")
            sb.appendLine("导出时间：${sdf.format(Date())}")
            sb.appendLine("共 ${records.size} 条记录")
            sb.appendLine()

            records.forEachIndexed { i, r ->
                val lockIcon = if (r.isLocked) "🔒" else "🔓"
                val nextStr = r.nextVaccinationTime?.let { " → 下次接种: ${sdf.format(Date(it))}" } ?: ""
                val noteStr = if (!r.note.isNullOrBlank()) "\n   备注: ${r.note}" else ""
                sb.appendLine("${i + 1}. $lockIcon ${r.vaccineName}")
                sb.appendLine("   接种时间: ${sdf.format(Date(r.vaccinationTime))}$nextStr$noteStr")
                sb.appendLine()
            }

            sb.appendLine("========== 导出结束 ==========")

            try {
                val dir = File(Constants.EXPORT_DIR)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "vaccination_records_${System.currentTimeMillis()}.txt")
                file.writeText(sb.toString())
                Toast.makeText(requireContext(), "导出成功：${file.absolutePath}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 疫苗记录列表适配器 */
class VaccineListViewAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<VaccineListViewAdapter.VH>() {

    private var records = emptyList<VaccinationRecord>()

    fun submitList(list: List<VaccinationRecord>) {
        records = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = android.widget.TextView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(16, 4, 16, 4) }
            textSize = 14f
            setPadding(12, 12, 12, 12)
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = records[position]
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val nextStr = r.nextVaccinationTime?.let { " → 下次: ${sdf.format(Date(it))}" } ?: ""
        val noteStr = if (!r.note.isNullOrBlank()) "\n📝 ${r.note}" else ""
        val lockIcon = if (r.isLocked) "🔒" else "🔓"
        holder.tv.text = "$lockIcon ${r.vaccineName}\n接种: ${sdf.format(Date(r.vaccinationTime))}$nextStr$noteStr"
    }

    override fun getItemCount() = records.size

    inner class VH(val tv: android.widget.TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv)
}