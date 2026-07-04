// BabyCare/app/src/main/java/com/babycare/ui/SettingsFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.babycare.R
import com.babycare.data.SettingsManager
import com.babycare.databinding.FragmentSettingsBinding
import com.babycare.util.IconChanger
import com.babycare.util.WebDavUtil
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val settings by lazy { SettingsManager(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadWebDavConfig()
        loadThemeConfig()
        loadIconConfig()
        setupUI()
    }

    // ═══════════════════ WebDAV ═══════════════════

    private fun loadWebDavConfig() {
        binding.etWebDavUrl.setText(settings.getWebDavUrl())
        binding.etWebDavUser.setText(settings.getWebDavUser())
        binding.etWebDavPass.setText(settings.getWebDavPass())
        binding.switchAutoSync.isChecked = settings.isWebDavAutoSync()
        updateSyncStatus()
    }

    private fun updateSyncStatus() {
        val lastSync = settings.getLastWebDavSyncTime()
        if (lastSync > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.tvSyncStatus.text = "上次同步：${sdf.format(Date(lastSync))}"
        } else {
            binding.tvSyncStatus.text = "尚未备份"
        }
    }

    private fun setupUI() {
        // ─── WebDAV ───
        binding.btnWebDavSave.setOnClickListener { saveWebDavConfig() }
        binding.btnWebDavBackup.setOnClickListener { doWebDavBackup() }
        binding.switchAutoSync.setOnCheckedChangeListener { _, checked ->
            settings.saveWebDavAutoSync(checked)
        }

        // ─── 主题 ───
        binding.rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbThemeLight -> "light"
                R.id.rbThemeDark -> "dark"
                else -> "system"
            }
            settings.saveThemeMode(mode)
            val modeValue = when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(modeValue)
        }

        // ─── 图标（6色） ───
        val current = IconChanger.getCurrentIcon(requireContext())
        updateIconLabel(current)
        binding.btnIconOrange.setOnClickListener { switchIcon(IconChanger.ICON_ORANGE) }
        binding.btnIconBlue.setOnClickListener { switchIcon(IconChanger.ICON_BLUE) }
        binding.btnIconPink.setOnClickListener { switchIcon(IconChanger.ICON_PINK) }
        binding.btnIconGreen.setOnClickListener { switchIcon(IconChanger.ICON_GREEN) }
        binding.btnIconPurple.setOnClickListener { switchIcon(IconChanger.ICON_PURPLE) }
        binding.btnIconTeal.setOnClickListener { switchIcon(IconChanger.ICON_TEAL) }
    }

    private fun saveWebDavConfig() {
        val url = binding.etWebDavUrl.text.toString().trim()
        val user = binding.etWebDavUser.text.toString().trim()
        val pass = binding.etWebDavPass.text.toString().trim()

        if (url.isBlank()) {
            Toast.makeText(requireContext(), "请输入WebDAV地址", Toast.LENGTH_SHORT).show()
            return
        }

        settings.saveWebDavUrl(url)
        settings.saveWebDavUser(user)
        settings.saveWebDavPass(pass)

        // 保存后自动备份
        doWebDavBackup()
    }

    private fun doWebDavBackup() {
        binding.btnWebDavBackup.isEnabled = false
        binding.tvSyncStatus.text = "⏳ 正在备份..."
        WebDavUtil.backup(requireContext()) { success, message ->
            requireActivity().runOnUiThread {
                binding.btnWebDavBackup.isEnabled = true
                binding.tvSyncStatus.text = if (success) "✅ $message" else "❌ $message"
                if (success) updateSyncStatus()
            }
        }
    }

    // ═══════════════════ 主题 ═══════════════════

    private fun loadThemeConfig() {
        val mode = settings.getThemeMode()
        when (mode) {
            "light" -> binding.rbThemeLight.isChecked = true
            "dark" -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }
    }

    // ═══════════════════ 图标 ═══════════════════

    private fun loadIconConfig() {
        val current = IconChanger.getCurrentIcon(requireContext())
        updateIconLabel(current)
    }

    private fun switchIcon(iconName: String) {
        IconChanger.setIcon(requireContext(), iconName)
        updateIconLabel(iconName)
        Toast.makeText(requireContext(), "图标已切换为${IconChanger.getIconLabel(iconName)}", Toast.LENGTH_SHORT).show()
    }

    private fun updateIconLabel(iconName: String) {
        binding.tvCurrentIcon.text = "当前：${IconChanger.getIconLabel(iconName)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}