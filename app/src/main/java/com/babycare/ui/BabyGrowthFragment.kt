// BabyCare/app/src/main/java/com/babycare/ui/BabyGrowthFragment.kt
package com.babycare.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.BabyProfile
import com.babycare.data.SettingsManager
import com.babycare.databinding.FragmentBabyGrowthBinding
import com.babycare.util.AgeCalculator
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BabyGrowthFragment : Fragment() {
    private var _binding: FragmentBabyGrowthBinding? = null
    private val binding get() = _binding!!
    private val settings by lazy { SettingsManager(requireContext()) }
    private val babyDao by lazy { (requireActivity().application as BabyCareApp).database.babyDao() }

    private var birthDate: Long = 0
    private var birthLocked = false
    private var weight = 0f
    private var weightLocked = false
    private var currentProfile: BabyProfile? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBabyGrowthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadBabyProfile()
        applyCustomColors()
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
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        binding.tvBirthDate.text = if (birthDate > 0) sdf.format(Date(birthDate)) else "未设置"
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
            "day" -> "当前宝宝已经 $totalDays 天"
            "week" -> "当前宝宝已经 ${totalDays / 7} 周 ${totalDays % 7} 天"
            "month" -> "当前宝宝已经 $months 月 ${weeks} 周 ${days} 天"
            else -> "当前宝宝已经 $totalDays 天"
        }
        binding.tvBabyAge.text = text
    }

    private fun updateWeightUI() {
        val unit = settings.getWeightUnit()
        val displayWeight = if (unit == "jin" && weight > 0f) weight * 2f else weight
        val unitLabel = if (unit == "jin") "斤" else "kg"
        binding.tvWeight.text = if (weight > 0f) "%.1f %s".format(displayWeight, unitLabel) else "-- $unitLabel"
        binding.btnLockWeight.text = if (weightLocked) "🔓 已锁定" else "🔒 锁定"
        binding.etWeightInput.isEnabled = !weightLocked
        binding.btnSaveWeight.isEnabled = !weightLocked
        // 同步RadioGroup状态
        if (unit == "jin") binding.rbWeightJin.isChecked = true else binding.rbWeightKg.isChecked = true
    }

    /** 应用自定义配色方案 */
    private fun applyCustomColors() {
        try {
            val layoutColor = AndroidColor.parseColor(settings.getLayoutColor())
            val fontColor = AndroidColor.parseColor(settings.getFontColor())
            binding.root.setBackgroundColor(layoutColor)
            for (i in 0 until (binding.root as? android.widget.ScrollView)?.childCount ?: 0) {
                val child = (binding.root as android.widget.ScrollView).getChildAt(i)
                applyFontColorToViewGroup(child as? android.view.ViewGroup, fontColor)
            }
        } catch (_: Exception) {}
    }

    private fun applyFontColorToViewGroup(group: android.view.ViewGroup?, color: Int) {
        group?.let { g ->
            for (i in 0 until g.childCount) {
                val child = g.getChildAt(i)
                if (child is android.widget.TextView) {
                    if (child.currentTextColor != 0 && child.isEnabled) {
                        try { child.setTextColor(color) } catch (_: Exception) {}
                    }
                }
                if (child is android.view.ViewGroup) {
                    applyFontColorToViewGroup(child, color)
                }
            }
        }
    }

    private fun setupUI() {
        // 生日
        binding.btnEditBirth.setOnClickListener { showBirthDatePicker() }
        binding.btnLockBirth.setOnClickListener { toggleBirthLock() }

        // 年龄单位
        val currentUnit = settings.getAgeUnit()
        when (currentUnit) {
            "day" -> binding.rbAgeDay.isChecked = true
            "week" -> binding.rbAgeWeek.isChecked = true
            "month" -> binding.rbAgeMonth.isChecked = true
        }
        binding.rgAgeUnit.setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                com.babycare.R.id.rbAgeDay -> "day"
                com.babycare.R.id.rbAgeWeek -> "week"
                else -> "month"
            }
            settings.saveAgeUnit(unit)
            updateAgeDisplay()
        }

        // 体重
        binding.btnSaveWeight.setOnClickListener { saveWeight() }
        binding.btnLockWeight.setOnClickListener { toggleWeightLock() }
        binding.rgWeightUnit.setOnCheckedChangeListener { _, checkedId ->
            val unit = if (checkedId == com.babycare.R.id.rbWeightJin) "jin" else "kg"
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
        saveProfile()
        Toast.makeText(requireContext(), "体重已更新为 ${weight}kg", Toast.LENGTH_SHORT).show()
    }

    private fun toggleWeightLock() {
        weightLocked = !weightLocked
        updateWeightUI()
        saveProfile()
    }

    private fun saveProfile() {
        lifecycleScope.launch {
            if (currentProfile != null) {
                babyDao.upsertProfile(currentProfile!!.copy(
                    birthDate = birthDate,
                    isLocked = birthLocked,
                    weight = weight,
                    weightLocked = weightLocked
                ))
            } else {
                babyDao.upsertProfile(BabyProfile(
                    birthDate = birthDate,
                    isLocked = birthLocked,
                    weight = weight,
                    weightLocked = weightLocked
                ))
            }
            loadBabyProfile()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
