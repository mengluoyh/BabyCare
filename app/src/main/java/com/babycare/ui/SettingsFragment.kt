// BabyCare/app/src/main/java/com/babycare/ui/SettingsFragment.kt
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
import com.babycare.databinding.FragmentSettingsBinding
import com.babycare.util.AgeCalculator
import com.babycare.util.IconChanger
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val settings by lazy { SettingsManager(requireContext()) }
    private val babyDao by lazy { (requireActivity().application as BabyCareApp).database.babyDao() }

    private var birthDate: Long = 0
    private var birthLocked = false
    private var currentProfile: BabyProfile? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
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
                updateBirthUI()
                updateAgeDisplay()
            }
        }
    }

    private fun updateBirthUI() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        binding.tvBirthDate.text = if (birthDate > 0) sdf.format(Date(birthDate)) else "未设置"
        binding.btnLockBirth.text = if (birthLocked) "🔓 已锁定" else "🔒 锁定"
        binding.btnEditBirth.isEnabled = birthLocked
    }

    private fun updateAgeDisplay() {
        if (birthDate <= 0) {
            binding.tvBabyAge.text = "👶 请先设置宝宝出生日期"
            return
        }
        val (months, weeks, days) = AgeCalculator.calculateAge(birthDate)
        val totalDays = AgeCalculator.totalDays(birthDate)

        val unit = settings.getAgeUnit()
        val text = when (unit) {
            "day" -> "当前宝宝已经 ${totalDays} 天"
            "week" -> "当前宝宝已经 ${totalDays / 7} 周 ${totalDays % 7} 天"
            "month" -> "当前宝宝已经 ${months} 月 ${weeks} 周 ${days} 天"
            else -> "当前宝宝已经 ${totalDays} 天"
        }
        binding.tvBabyAge.text = text
    }

    private fun setupUI() {
        // 生日按钮
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

        // 图标切换
        val current = IconChanger.getCurrentIcon(requireContext())
        updateIconLabel(current)
        binding.btnIconOrange.setOnClickListener { switchIcon(IconChanger.ICON_ORANGE) }
        binding.btnIconBlue.setOnClickListener { switchIcon(IconChanger.ICON_BLUE) }
        binding.btnIconPink.setOnClickListener { switchIcon(IconChanger.ICON_PINK) }
    }

    private fun switchIcon(iconName: String) {
        IconChanger.setIcon(requireContext(), iconName)
        updateIconLabel(iconName)
        Toast.makeText(requireContext(), "图标已切换为${iconName}色，返回桌面查看", Toast.LENGTH_SHORT).show()
    }

    private fun updateIconLabel(iconName: String) {
        val label = when (iconName) {
            IconChanger.ICON_ORANGE -> "当前：🟠 橙色"
            IconChanger.ICON_BLUE -> "当前：🔵 蓝色"
            IconChanger.ICON_PINK -> "当前：🩷 粉色"
            else -> "当前：🟠 橙色"
        }
        binding.tvCurrentIcon.text = label
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
        binding.btnEditBirth.isEnabled = birthLocked
        lifecycleScope.launch {
            if (currentProfile != null) {
                babyDao.upsertProfile(currentProfile!!.copy(birthDate = birthDate, isLocked = birthLocked))
            } else {
                babyDao.upsertProfile(BabyProfile(birthDate = birthDate, isLocked = birthLocked))
            }
            loadBabyProfile()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}