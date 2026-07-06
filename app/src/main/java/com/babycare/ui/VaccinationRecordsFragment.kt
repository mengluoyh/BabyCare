// BabyCare/app/src/main/java/com/babycare/ui/VaccinationRecordsFragment.kt
package com.babycare.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.R
import com.babycare.data.VaccinationRecord
import com.babycare.util.Constants
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VaccinationRecordsFragment : Fragment() {

    private var vaccineRecords = listOf<VaccinationRecord>()
    private var currentPage = 0
    private val pageSize = 3
    private lateinit var contentLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var pageInfoText: TextView
    private lateinit var pageInput: EditText
    private lateinit var prevBtn: MaterialButton
    private lateinit var nextBtn: MaterialButton
    private lateinit var paginationRow: LinearLayout
    private lateinit var cardContainer: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val dao get() = (requireActivity().application as BabyCareApp).database.vaccineDao()
    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        private val PRESET_COLORS = listOf(
            "#FF6B35" to "🟠 橙色",
            "#1976D2" to "🔵 蓝色",
            "#4CAF50" to "🟢 绿色",
            "#F44336" to "🔴 红色",
            "#7B61FF" to "🟣 紫色",
            "#201A17" to "⚫ 黑色"
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        swipeRefresh = SwipeRefreshLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(ScrollView(requireContext()).apply {
                isFillViewport = true
                setBackgroundColor(ContextCompat.getColor(context, R.color.background))

                contentLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                }.also { addView(it) }

            // 标题
            contentLayout.addView(TextView(context).apply {
                text = "💉 疫苗接种记录"
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })

            // 空状态
            emptyText = TextView(context).apply {
                text = "暂无疫苗接种记录"
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
                visibility = View.GONE
            }.also { contentLayout.addView(it) }

            // 添加按钮
            contentLayout.addView(MaterialButton(context).apply {
                text = "➕ 添加疫苗记录"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 12, 0, 0) }
                setOnClickListener { showVaccineDialog(null) }
            })

            // 记录卡片容器
            cardContainer = LinearLayout(context).apply {
                id = View.generateViewId()
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 12 }
            }.also { contentLayout.addView(it) }

            // 分页控件
            paginationRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 12, 0, 0) }
                visibility = View.GONE

                prevBtn = MaterialButton(context).apply {
                    text = "← 上一页"
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 0, 8, 0) }
                    setOnClickListener { goToPage(currentPage - 1) }
                }.also { addView(it) }

                pageInput = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    textSize = 14f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        140,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(4, 0, 4, 0) }
                    setOnEditorActionListener { _, _, _ ->
                        val p = text.toString().toIntOrNull()
                        if (p != null) goToPage(p - 1)
                        true
                    }
                }.also { addView(it) }

                pageInfoText = TextView(context).apply {
                    textSize = 14f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(4, 0, 4, 0) }
                }.also { addView(it) }

                nextBtn = MaterialButton(context).apply {
                    text = "下一页 →"
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(8, 0, 0, 0) }
                    setOnClickListener { goToPage(currentPage + 1) }
                }.also { addView(it) }
            }.also { contentLayout.addView(it) }

            // 导出按钮（底部中间）
            contentLayout.addView(LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_HORIZONTAL
                addView(MaterialButton(context).apply {
                    text = "📤 导出疫苗记录"
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 8, 0, 16) }
                    setOnClickListener { exportVaccineRecords() }
                })
            })
        }
    }
    return swipeRefresh
}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                com.babycare.util.SyncEngine.sync(requireContext())
                loadVaccines()
                swipeRefresh.isRefreshing = false
            }
        }
        loadVaccines()
    }

    override fun onResume() {
        super.onResume()
        loadVaccines()
    }

    private fun loadVaccines() {
        lifecycleScope.launch {
            vaccineRecords = dao.getAllSnapshot()
            emptyText.visibility = if (vaccineRecords.isEmpty()) View.VISIBLE else View.GONE
            paginationRow.visibility = if (vaccineRecords.isEmpty()) View.GONE else View.VISIBLE
            currentPage = 0
            renderPage()
        }
    }

    private fun renderPage() {
        val totalPages = ((vaccineRecords.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        if (currentPage >= totalPages) currentPage = totalPages - 1
        if (currentPage < 0) currentPage = 0

        val start = currentPage * pageSize
        val end = (start + pageSize).coerceAtMost(vaccineRecords.size)
        val pageRecords = if (vaccineRecords.isEmpty()) emptyList() else vaccineRecords.subList(start, end)

        // 更新卡片
        cardContainer.removeAllViews()
        for (r in pageRecords) {
            cardContainer.addView(buildRecordCard(r))
        }

        // 更新分页控件
        pageInfoText.text = "/ $totalPages 页"
        pageInput.setText("${currentPage + 1}")
        prevBtn.isEnabled = currentPage > 0
        nextBtn.isEnabled = currentPage < totalPages - 1
    }

    private fun goToPage(page: Int) {
        val totalPages = ((vaccineRecords.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        if (page < 0 || page >= totalPages) return
        currentPage = page
        renderPage()
    }

    /** 构建单条记录卡片 */
    private fun buildRecordCard(r: VaccinationRecord): View {
        val fontColor = r.fontColor?.let { hex ->
            try { android.graphics.Color.parseColor(hex) } catch (e: Exception) { null }
        }

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.setMargins(0, 0, 0, 8)
                it.topMargin = 8
            }
            setPadding(16, 12, 16, 12)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
        }

        // 第1行：疫苗名称 + 接种日期
        val row1 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(row1)

        row1.addView(TextView(requireContext()).apply {
            text = r.vaccineName
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            if (fontColor != null) setTextColor(fontColor)
        })

        row1.addView(TextView(requireContext()).apply {
            text = DATE_FMT.format(Date(r.vaccinationTime))
            textSize = 14f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (fontColor != null) setTextColor(fontColor)
        })

        // 下次接种（如果有）
        if (r.nextVaccinationTime != null) {
            val nextName = if (!r.nextVaccineName.isNullOrBlank()) " ${r.nextVaccineName}" else ""
            card.addView(TextView(requireContext()).apply {
                text = "下次接种${nextName}: ${DATE_FMT.format(Date(r.nextVaccinationTime))}"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 4 }
                if (fontColor != null) setTextColor(fontColor)
                else setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            })
        }

        // 备注（如果有）
        if (!r.note.isNullOrBlank()) {
            card.addView(TextView(requireContext()).apply {
                text = "备注: ${r.note}"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 4 }
                if (fontColor != null) setTextColor(fontColor)
                else setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            })
        }

        // 操作按钮行
        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 8 }
            gravity = Gravity.END
        }
        card.addView(actions)

        actions.addView(MaterialButton(requireContext()).apply {
            text = "编辑"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 4, 0) }
            setOnClickListener { showVaccineDialog(r) }
        })

        actions.addView(MaterialButton(requireContext()).apply {
            text = "✕ 删除"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { confirmDelete(r) }
        })

        return card
    }

    /** 添加/编辑疫苗记录对话框（移除了时间选择，只选日期） */
    private fun showVaccineDialog(existing: VaccinationRecord?) {
        val isEdit = existing != null
        val view = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }

        // 疫苗名称
        val etName = EditText(requireContext()).apply {
            hint = "疫苗名称 *"
            setText(existing?.vaccineName ?: "")
            setPadding(0, 8, 0, 8)
        }
        view.addView(etName)

        // 接种日期（仅日期选择，移除了时间）
        val etVaccinationDate = EditText(requireContext()).apply {
            hint = "接种日期 *"
            setText(existing?.vaccinationTime?.let { DATE_FMT.format(Date(it)) } ?: "")
            setPadding(0, 8, 0, 8)
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val cal = Calendar.getInstance()
                if (existing != null) cal.timeInMillis = existing.vaccinationTime
                DatePickerDialog(requireContext(), { _, y, m, d ->
                    cal.set(y, m, d, 12, 0, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    setText(DATE_FMT.format(cal.time))
                    tag = cal.timeInMillis
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            if (existing != null) tag = existing.vaccinationTime
        }
        view.addView(etVaccinationDate)

        // 下次接种日期
        val etNextDate = EditText(requireContext()).apply {
            hint = "下次接种日期（可选）"
            setText(existing?.nextVaccinationTime?.let { DATE_FMT.format(Date(it)) } ?: "")
            setPadding(0, 8, 0, 8)
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val cal = Calendar.getInstance()
                if (existing?.nextVaccinationTime != null) cal.timeInMillis = existing.nextVaccinationTime
                DatePickerDialog(requireContext(), { _, y, m, d ->
                    cal.set(y, m, d, 12, 0, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    setText(DATE_FMT.format(cal.time))
                    tag = cal.timeInMillis
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            if (existing?.nextVaccinationTime != null) tag = existing.nextVaccinationTime
        }
        view.addView(etNextDate)

        // 下次疫苗名称
        val etNextName = EditText(requireContext()).apply {
            hint = "下次疫苗名称（可选）"
            setText(existing?.nextVaccineName ?: "")
            setPadding(0, 8, 0, 8)
        }
        view.addView(etNextName)

        // 备注
        val etNote = EditText(requireContext()).apply {
            hint = "备注（可选）"
            setText(existing?.note ?: "")
            setPadding(0, 8, 0, 8)
        }
        view.addView(etNote)

        // 字体颜色选择
        var selectedColorHex: String? = existing?.fontColor
        view.addView(TextView(requireContext()).apply {
            text = "字体颜色:"
            textSize = 14f
            setPadding(0, 8, 0, 4)
        })
        val colorRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        view.addView(colorRow)

        val colorDots = mutableListOf<View>()
        for ((hex, _) in PRESET_COLORS) {
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(44, 44).also {
                    it.setMargins(4, 4, 4, 4)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(hex))
                    setSize(44, 44)
                }
                setOnClickListener {
                    selectedColorHex = hex
                    for (v in colorDots) v.alpha = 0.35f
                    alpha = 1f
                }
                alpha = if (hex == selectedColorHex) 1f else 0.35f
            }
            colorRow.addView(dot)
            colorDots.add(dot)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) "编辑疫苗记录" else "添加疫苗记录")
            .setView(view)
            .setPositiveButton(if (isEdit) "保存" else "添加") { _, _ ->
                val name = etName.text.toString().trim()
                val dateText = etVaccinationDate.text.toString().trim()
                if (name.isEmpty() || dateText.isEmpty()) {
                    Toast.makeText(requireContext(), "疫苗名称和接种日期不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val parsedTime = (etVaccinationDate.tag as? Long)
                    ?: DATE_FMT.parse(dateText)?.time
                if (parsedTime == null || parsedTime <= 0) {
                    Toast.makeText(requireContext(), "接种日期无效", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val nextTime = etNextDate.tag as? Long
                    ?: etNextDate.text.toString().trim().let {
                        if (it.isNotEmpty()) DATE_FMT.parse(it)?.time else null
                    }
                lifecycleScope.launch {
                    dao.upsert(VaccinationRecord(
                        id = existing?.id ?: 0,
                        vaccineName = name,
                        vaccinationTime = parsedTime,
                        nextVaccinationTime = nextTime,
                        nextVaccineName = etNextName.text.toString().trim().ifEmpty { null },
                        isLocked = existing?.isLocked ?: false,
                        note = etNote.text.toString().trim().ifEmpty { null },
                        fontColor = selectedColorHex
                    ))
                    loadVaccines()
                    Toast.makeText(requireContext(), if (isEdit) "✅ 已更新" else "✅ 已添加", Toast.LENGTH_SHORT).show()
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
                    dao.softDelete(record.id, System.currentTimeMillis())
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
            sb.appendLine("导出时间：${DATE_FMT.format(Date())}")
            sb.appendLine("共 ${records.size} 条记录")
            sb.appendLine()

            records.forEachIndexed { i, r ->
                val nextStr = r.nextVaccinationTime?.let {
                    val nameStr = if (!r.nextVaccineName.isNullOrBlank()) " ${r.nextVaccineName}" else ""
                    " | 下次${nameStr}: ${DATE_FMT.format(Date(it))}"
                } ?: ""
                val noteStr = if (!r.note.isNullOrBlank()) "\n   备注: ${r.note}" else ""
                sb.appendLine("${i + 1}. ${r.vaccineName} | 接种日期: ${DATE_FMT.format(Date(r.vaccinationTime))}$nextStr$noteStr")
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