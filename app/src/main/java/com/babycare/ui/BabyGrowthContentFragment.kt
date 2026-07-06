// BabyCare/app/src/main/java/com/babycare/ui/BabyGrowthContentFragment.kt
package com.babycare.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.babycare.BabyCareApp
import com.babycare.R
import com.babycare.data.BabyProfile
import com.babycare.data.SettingsManager
import com.babycare.data.VaccinationRecord
import com.babycare.data.WeightRecord
import com.babycare.databinding.FragmentBabyGrowthContentBinding
import com.babycare.util.AgeCalculator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BabyGrowthContentFragment : Fragment() {
    private var _binding: FragmentBabyGrowthContentBinding? = null
    private val binding get() = _binding!!
    private val settings by lazy { SettingsManager(requireContext()) }
    private val babyDao by lazy { (requireActivity().application as BabyCareApp).database.babyDao() }
    private val vaccineDao by lazy { (requireActivity().application as BabyCareApp).database.vaccineDao() }
    private val weightDao by lazy { (requireActivity().application as BabyCareApp).database.weightDao() }

    private var birthDate: Long = 0
    private var birthLocked = false
    private var weight = 0f
    private var weightLocked = false
    private var currentProfile: BabyProfile? = null

    private var selectedVaccinationTime: Long = 0L
    private var selectedNextVaccinationTime: Long? = null
    private lateinit var vaccineAdapter: VaccineListAdapter

    companion object {
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private val BIRTH_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBabyGrowthContentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupVaccineUI()
        loadBabyProfile()
        loadVaccines()
    }

    private fun loadBabyProfile() {
        lifecycleScope.launch {
            val profile = babyDao.getProfileSync()
            currentProfile = profile
            if (profile != null) {
                birthDate = profile.birthDate
                birthLocked = profile.isLocked
                weight = profile.weight
                weightLocked = profile.weightLocked
                updateBirthUI()
                updateAgeDisplay()
                updateWeightUI()
            }
        }
    }

    private fun updateBirthUI() {
        binding.tvBirthDate.text = if (birthDate > 0) BIRTH_FMT.format(Date(birthDate)) else "未设置"
        binding.btnLockBirth.text = if (birthLocked) "🔓 已锁定" else "🔒 锁定"
        binding.btnEditBirth.isEnabled = !birthLocked
    }

    private fun updateAgeDisplay() {
        if (birthDate <= 0) {
            binding.tvBabyAge.text = "👶 请先设置宝宝出生日期"
            return
        }
        val unit = settings.getAgeUnit()
        val (months, weeks, days) = AgeCalculator.calculateAge(birthDate)
        val totalDays = AgeCalculator.totalDays(birthDate)
        val text = when (unit) {
            "day" -> "宝宝 $totalDays 天啦"
            "week" -> "宝宝 ${totalDays / 7} 周 ${totalDays % 7} 天啦"
            "month" -> "宝宝 $months 月 ${weeks} 周 ${days} 天啦"
            else -> "宝宝 $totalDays 天啦"
        }
        binding.tvBabyAge.text = text
    }

    private fun updateWeightUI() {
        val unit = settings.getWeightUnit()
        val unitLabel = if (unit == "jin") "斤" else "kg"
        binding.tvWeight.text = if (weight > 0f) "%.1f %s".format(weight, unitLabel) else "-- $unitLabel"
        binding.btnLockWeight.text = if (weightLocked) "🔓 已锁定" else "🔒 锁定"
        binding.etWeightInput.isEnabled = !weightLocked
        binding.btnSaveWeight.isEnabled = !weightLocked
        if (unit == "jin") binding.rbWeightJin.isChecked = true else binding.rbWeightKg.isChecked = true
    }

    private fun setupUI() {
        binding.btnEditBirth.setOnClickListener { showBirthDatePicker() }
        binding.btnLockBirth.setOnClickListener { toggleBirthLock() }

        val currentUnit = settings.getAgeUnit()
        when (currentUnit) {
            "day" -> binding.rbAgeDay.isChecked = true
            "week" -> binding.rbAgeWeek.isChecked = true
            "month" -> binding.rbAgeMonth.isChecked = true
        }
        binding.rgAgeUnit.setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                R.id.rbAgeDay -> "day"
                R.id.rbAgeWeek -> "week"
                else -> "month"
            }
            settings.saveAgeUnit(unit)
            updateAgeDisplay()
        }

        binding.btnSaveWeight.setOnClickListener { saveWeight() }
        binding.btnLockWeight.setOnClickListener { toggleWeightLock() }
        binding.rgWeightUnit.setOnCheckedChangeListener { _, checkedId ->
            val unit = if (checkedId == R.id.rbWeightJin) "jin" else "kg"
            settings.saveWeightUnit(unit)
            updateWeightUI()
        }
    }

    private fun showBirthDatePicker() {
        val cal = Calendar.getInstance()
        if (birthDate > 0) cal.timeInMillis = birthDate
        DatePickerDialog(requireContext(), { _, year, month, day ->
            cal.set(year, month, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            birthDate = cal.timeInMillis
            updateBirthUI()
            updateAgeDisplay()
            if (!birthLocked) toggleBirthLock()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun toggleBirthLock() {
        birthLocked = !birthLocked
        binding.btnLockBirth.text = if (birthLocked) "🔓 已锁定" else "🔒 锁定"
        binding.btnEditBirth.isEnabled = !birthLocked
        saveProfile()
    }

    private fun saveWeight() {
        val text = binding.etWeightInput.text.toString()
        val w = text.toFloatOrNull()
        if (w == null || w <= 0f) {
            Toast.makeText(requireContext(), "请输入有效体重", Toast.LENGTH_SHORT).show()
            return
        }
        weight = w
        updateWeightUI()
        binding.etWeightInput.text?.clear()

        // 保存体重历史记录
        lifecycleScope.launch {
            weightDao.insert(WeightRecord(weight = w))
        }

        saveProfile()
        Toast.makeText(requireContext(), "体重已保存", Toast.LENGTH_SHORT).show()
    }

    private fun toggleWeightLock() {
        weightLocked = !weightLocked
        updateWeightUI()
        saveProfile()
    }

    private fun saveProfile() {
        lifecycleScope.launch {
            val profile = currentProfile?.copy(
                birthDate = birthDate,
                isLocked = birthLocked,
                weight = weight,
                weightLocked = weightLocked
            ) ?: BabyProfile(
                birthDate = birthDate,
                isLocked = birthLocked,
                weight = weight,
                weightLocked = weightLocked
            )
            babyDao.upsertProfile(profile)
            loadBabyProfile()
        }
    }

    // ═══════════════════ 疫苗接种管理 ═══════════════════

    private var editingVaccineRecord: VaccinationRecord? = null

    private fun setupVaccineUI() {
        vaccineAdapter = VaccineListAdapter { record -> deleteVaccine(record) }
        binding.vaccineRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.vaccineRecyclerView.adapter = vaccineAdapter

        binding.etVaccinationTime.setOnClickListener { showDateTimePicker { time ->
            selectedVaccinationTime = time
            binding.etVaccinationTime.setText(DATE_FMT.format(Date(time)))
        }}

        binding.etNextVaccination.setOnClickListener { showDateTimePicker { time ->
            selectedNextVaccinationTime = time
            binding.etNextVaccination.setText(DATE_FMT.format(Date(time)))
        }}

        binding.btnSaveVaccine.setOnClickListener { saveVaccine() }
        binding.btnUnlockVaccine.setOnClickListener { unlockLastVaccine() }
    }

    /** 日期+时间选择器（日期 + 时分） */
    private fun showDateTimePicker(onSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            cal.set(year, month, day, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), 0)
            cal.set(Calendar.MILLISECOND, 0)
            // 弹出时间选择器
            TimePickerDialog(requireContext(), { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                onSelected(cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveVaccine() {
        val name = binding.etVaccineName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "请输入疫苗名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedVaccinationTime <= 0) {
            Toast.makeText(requireContext(), "请选择接种时间", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val existingId = editingVaccineRecord?.id ?: 0
            val record = VaccinationRecord(
                id = existingId,
                vaccineName = name,
                vaccinationTime = selectedVaccinationTime,
                nextVaccinationTime = selectedNextVaccinationTime,
                nextVaccineName = binding.etNextVaccineName.text.toString().trim().takeIf { it.isNotEmpty() },
                isLocked = false,
                note = binding.etVaccineNote.text.toString().trim().takeIf { it.isNotEmpty() }
            )
            vaccineDao.upsert(record)
            Toast.makeText(requireContext(), "✅ 疫苗接种记录已保存", Toast.LENGTH_SHORT).show()
            // 保存后不锁定、不清空输入框、不清除 editingVaccineRecord
            loadVaccines()
        }
    }

    private fun unlockLastVaccine() {
        lifecycleScope.launch {
            val locked = vaccineDao.getFirstLocked()
            if (locked != null) {
                val unlocked = locked.copy(isLocked = false)
                vaccineDao.upsert(unlocked)
                Toast.makeText(requireContext(), "🔓 已解锁「${locked.vaccineName}」，可修改", Toast.LENGTH_SHORT).show()
                // 预填数据到输入框
                editingVaccineRecord = unlocked
                binding.etVaccineName.setText(unlocked.vaccineName)
                if (unlocked.vaccinationTime > 0) {
                    selectedVaccinationTime = unlocked.vaccinationTime
                    binding.etVaccinationTime.setText(DATE_FMT.format(Date(unlocked.vaccinationTime)))
                }
                if (unlocked.nextVaccinationTime != null && unlocked.nextVaccinationTime!! > 0) {
                    selectedNextVaccinationTime = unlocked.nextVaccinationTime
                    binding.etNextVaccination.setText(DATE_FMT.format(Date(unlocked.nextVaccinationTime!!)))
                }
                binding.etNextVaccineName.setText(unlocked.nextVaccineName ?: "")
                binding.etVaccineNote.setText(unlocked.note ?: "")
                enableVaccineInput()
                loadVaccines()
            } else {
                Toast.makeText(requireContext(), "没有已锁定的记录可解锁", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enableVaccineInput() {
        binding.etVaccineName.isEnabled = true
        binding.etVaccinationTime.isEnabled = true
        binding.etNextVaccination.isEnabled = true
        binding.etNextVaccineName.isEnabled = true
        binding.etVaccineNote.isEnabled = true
        binding.btnSaveVaccine.isEnabled = true
    }

    private fun deleteVaccine(record: VaccinationRecord) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("删除确认")
            .setMessage("确定删除疫苗「${record.vaccineName}」记录？")
            .setPositiveButton("删除") { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    vaccineDao.delete(record)
                    if (editingVaccineRecord?.id == record.id) {
                        editingVaccineRecord = null
                        clearVaccineInput()
                    }
                    loadVaccines()
                }
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadVaccines() {
        lifecycleScope.launch {
            val records = vaccineDao.getAllSnapshot()
            vaccineAdapter.submitList(records.toMutableList() as MutableList<VaccinationRecord?>)
            binding.btnUnlockVaccine.isEnabled = records.any { it.isLocked }
        }
    }

    private fun clearVaccineInput() {
        binding.etVaccineName.text?.clear()
        binding.etVaccinationTime.text?.clear()
        binding.etNextVaccination.text?.clear()
        binding.etNextVaccineName.text?.clear()
        binding.etVaccineNote.text?.clear()
        selectedVaccinationTime = 0L
        selectedNextVaccinationTime = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── 疫苗列表适配器（ListAdapter + DiffUtil） ───────────

    inner class VaccineListAdapter(
        private val onDelete: (VaccinationRecord) -> Unit
    ) : ListAdapter<VaccinationRecord, VaccineListAdapter.VH>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = android.widget.TextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 4, 0, 4) }
                textSize = 13f
                setPadding(8, 8, 8, 8)
                setBackgroundColor(requireContext().getColor(R.color.surface_variant))
                setTextColor(requireContext().getColor(R.color.on_background))
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = getItem(position)
            val nextStr = r.nextVaccinationTime?.let {
                val nameStr = if (!r.nextVaccineName.isNullOrBlank()) " ${r.nextVaccineName}" else ""
                " → 下次${nameStr}: ${DATE_FMT.format(Date(it))}"
            } ?: ""
            val noteStr = if (!r.note.isNullOrBlank()) " · ${r.note}" else ""
            val lockIcon = if (r.isLocked) "🔒" else "🔓"
            holder.tv.text = "$lockIcon ${r.vaccineName}\n接种: ${DATE_FMT.format(Date(r.vaccinationTime))}$nextStr$noteStr"
            holder.itemView.setOnLongClickListener {
                onDelete(r)
                true
            }
        }

        inner class VH(val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv)
    }

    class DiffCallback : DiffUtil.ItemCallback<VaccinationRecord>() {
        override fun areItemsTheSame(old: VaccinationRecord, new: VaccinationRecord): Boolean =
            old.id == new.id

        override fun areContentsTheSame(old: VaccinationRecord, new: VaccinationRecord): Boolean =
            old == new
    }
}