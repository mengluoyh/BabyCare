// BabyCare/app/src/main/java/com/babycare/ui/VaccinationRecordsFragment.kt
package com.babycare.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.babycare.BabyCareApp
import com.babycare.data.VaccinationRecord
import com.babycare.util.Constants
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VaccinationRecordsFragment : Fragment() {

    private var vaccineRecords = listOf<VaccinationRecord>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VaccineListViewAdapter
    private lateinit var emptyText: android.widget.TextView
    private val dao get() = (requireActivity().application as BabyCareApp).database.vaccineDao()

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
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
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
                val rv = RecyclerView(context).apply {
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
                val exportBtn = MaterialButton(context).apply {
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
        adapter = VaccineListViewAdapter(
            onItemClick = { record -> showVaccineDialog(record) },
            onDeleteClick = { record -> confirmDelete(record) }
        )
        recyclerView.adapter = adapter
        loadVaccines()
    }

    override fun onResume() {
        super.onResume()
        loadVaccines()
    }

    private fun loadVaccines() {
        lifecycleScope.launch {
            vaccineRecords = dao.getAllSnapshot()
            adapter.submitList(vaccineRecords)
            emptyText.visibility = if (vaccineRecords.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (vaccineRecords.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** 显示添加/编辑疫苗记录对话框 */
    private fun showVaccineDialog(existing: VaccinationRecord?) {
        val isEdit = existing != null
        val inflater = LayoutInflater.from(requireContext())
        val view = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }

        // 疫苗名称
        val etName = android.widget.EditText(requireContext()).apply {
            hint = "疫苗名称 *"
            setText(existing?.vaccineName ?: "")
            setPadding(0, 8, 0, 8)
        }
        view.addView(etName)

        // 接种时间
        val etVaccinationTime = android.widget.EditText(requireContext()).apply {
            hint = "接种时间 *"
            setText(existing?.vaccinationTime?.let { sdf.format(Date(it)) } ?: "")
            setPadding(0, 8, 0, 8)
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val cal = Calendar.getInstance()
                if (existing != null) cal.timeInMillis = existing.vaccinationTime
                DatePickerDialog(requireContext(), { _, y, m, d ->
                    TimePickerDialog(requireContext(), { _, h, min ->
                        cal.set(y, m, d, h, min, 0)
                        setText(sdf.format(cal.time))
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        view.addView(etVaccinationTime)

        // 下次接种时间（可选）
        val etNextTime = android.widget.EditText(requireContext()).apply {
            hint = "下次接种时间（可选）"
            setText(existing?.nextVaccinationTime?.let { sdf.format(Date(it)) } ?: "")
            setPadding(0, 8, 0, 8)
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val cal = Calendar.getInstance()
                if (existing?.nextVaccinationTime != null) cal.timeInMillis = existing.nextVaccinationTime
                DatePickerDialog(requireContext(), { _, y, m, d ->
                    TimePickerDialog(requireContext(), { _, h, min ->
                        cal.set(y, m, d, h, min, 0)
                        setText(sdf.format(cal.time))
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        view.addView(etNextTime)

        // 下次疫苗名称（可选）
        val etNextName = android.widget.EditText(requireContext()).apply {
            hint = "下次疫苗名称（可选）"
            setText(existing?.nextVaccineName ?: "")
            setPadding(0, 8, 0, 8)
        }
        view.addView(etNextName)

        // 备注（可选）
        val etNote = android.widget.EditText(requireContext()).apply {
            hint = "备注（可选）"
            setText(existing?.note ?: "")
            setPadding(0, 8, 0, 8)
        }
        view.addView(etNote)

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) "编辑疫苗记录" else "添加疫苗记录")
            .setView(view)
            .setPositiveButton(if (isEdit) "保存" else "添加") { _, _ ->
                val name = etName.text.toString().trim()
                val vaccinationTime = etVaccinationTime.text.toString().trim()
                if (name.isEmpty() || vaccinationTime.isEmpty()) {
                    Toast.makeText(requireContext(), "疫苗名称和接种时间不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val parsedTime = parseDate(vaccinationTime)
                if (parsedTime == null) {
                    Toast.makeText(requireContext(), "接种时间格式错误", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val nextTime = etNextTime.text.toString().trim().let {
                    if (it.isNotEmpty()) parseDate(it) else null
                }
                lifecycleScope.launch {
                    dao.upsert(VaccinationRecord(
                        id = existing?.id ?: 0,
                        vaccineName = name,
                        vaccinationTime = parsedTime,
                        nextVaccinationTime = nextTime,
                        nextVaccineName = etNextName.text.toString().trim().ifEmpty { null },
                        isLocked = existing?.isLocked ?: false,
                        note = etNote.text.toString().trim().ifEmpty { null }
                    ))
                    loadVaccines()
                    Toast.makeText(requireContext(), if (isEdit) "已更新" else "已添加", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 确认删除 */
    private fun confirmDelete(record: VaccinationRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除疫苗记录")
            .setMessage("确定删除「${record.vaccineName}」吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    dao.delete(record)
                    loadVaccines()
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportVaccineRecords() {
        lifecycleScope.launch {
            val records = vaccineRecords
            if (records.isEmpty()) {
                Toast.makeText(requireContext(), "没有疫苗记录可导出", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sb = StringBuilder()
            sb.appendLine("========== 疫苗接种记录导出 ==========")
            sb.appendLine("导出时间：${sdf.format(Date())}")
            sb.appendLine("共 ${records.size} 条记录")
            sb.appendLine()

            records.forEachIndexed { i, r ->
                val lockIcon = if (r.isLocked) "🔒" else "🔓"
                val nextNameStr = if (!r.nextVaccineName.isNullOrBlank()) " ${r.nextVaccineName}" else ""
                val nextStr = r.nextVaccinationTime?.let { " → 下次${nextNameStr}: ${sdf.format(Date(it))}" } ?: ""
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

    companion object {
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private fun parseDate(str: String): Long? {
            return try { sdf.parse(str)?.time } catch (e: Exception) { null }
        }
    }
}

/** 疫苗记录列表适配器（支持点击编辑 + 删除按钮） */
class VaccineListViewAdapter(
    private val onItemClick: (VaccinationRecord) -> Unit,
    private val onDeleteClick: (VaccinationRecord) -> Unit
) : ListAdapter<VaccinationRecord, VaccineListViewAdapter.VH>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val itemLayout = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(12, 4, 12, 4) }
            setPadding(12, 12, 12, 12)
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

        val tv = android.widget.TextView(parent.context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            textSize = 14f
            setTextColor(parent.context.getColor(com.babycare.R.color.on_background))
        }
        itemLayout.addView(tv)

        val deleteBtn = com.google.android.material.button.MaterialButton(parent.context).apply {
            text = "✕"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(8, 0, 0, 0) }
        }
        itemLayout.addView(deleteBtn)

        return VH(itemLayout, tv, deleteBtn)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = getItem(position)
        val nextStr = r.nextVaccinationTime?.let {
            val nameStr = if (!r.nextVaccineName.isNullOrBlank()) " ${r.nextVaccineName}" else ""
            " → 下次${nameStr}: ${DATE_FMT.format(Date(it))}"
        } ?: ""
        val noteStr = if (!r.note.isNullOrBlank()) "\n📝 ${r.note}" else ""
        val lockIcon = if (r.isLocked) "🔒" else "🔓"
        holder.tv.text = "$lockIcon ${r.vaccineName}\n接种: ${DATE_FMT.format(Date(r.vaccinationTime))}$nextStr$noteStr"

        holder.itemView.setOnClickListener { onItemClick(r) }
        holder.deleteBtn.setOnClickListener { onDeleteClick(r) }
    }

    inner class VH(itemView: View, val tv: android.widget.TextView, val deleteBtn: com.google.android.material.button.MaterialButton) : RecyclerView.ViewHolder(itemView)

    companion object {
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}

private class DiffCallback : DiffUtil.ItemCallback<VaccinationRecord>() {
    override fun areItemsTheSame(old: VaccinationRecord, new: VaccinationRecord) = old.id == new.id
    override fun areContentsTheSame(old: VaccinationRecord, new: VaccinationRecord) = old == new
}