// BabyCare/app/src/main/java/com/babycare/ui/BabyGrowthContentFragment.kt
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
import com.babycare.BabyCareApp
import com.babycare.R
import com.babycare.data.BabyProfile
import com.babycare.data.SettingsManager
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
    private val weightDao by lazy { (requireActivity().application as BabyCareApp).database.weightDao() }

    private var birthDate: Long = 0
    private var birthLocked = false
    private var weight = 0f
    private var weightLocked = false
    private var currentProfile: BabyProfile? = null

    companion object {
        private val BIRTH_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBabyGrowthContentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadBabyProfile()
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

        lifecycleScope.launch {
            // 保存体重历史记录 + Profile 在同一个协程顺序执行
            weightDao.insert(WeightRecord(weight = w))
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
            Toast.makeText(requireContext(), "体重已保存", Toast.LENGTH_SHORT).show()
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}