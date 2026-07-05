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
import com.babycare.util.BackupManager
import com.babycare.util.IconChanger

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
        loadBackupConfig()
        loadThemeConfig()
        loadIconConfig()
        setupUI()
    }

    // ═══════════════════ 备份 ═══════════════════

    private fun loadBackupConfig() {
        updateBackupStatus()
    }

    private fun updateBackupStatus() {
        // 列出本地备份文件数
        val localDir = java.io.File("/storage/emulated/0/BabyCare/backups")
        val count = if (localDir.exists()) localDir.listFiles()?.filter { it.name.startsWith("babycare_backup_") && it.name.endsWith(".json") }?.size ?: 0 else 0
        binding.tvLocalBackupCount.text = if (count > 0) "本地备份文件: $count 个" else "暂无本地备份"
        binding.tvSyncStatus.text = ""
    }

    private fun setupUI() {
        // ─── 备份 ───
        binding.btnLocalBackup.setOnClickListener { doLocalBackup() }

        // ─── 主题 ───
        binding.rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbThemeLight -> "light"
                R.id.rbThemeDark -> "dark"
                else -> "system"
            }
            settings.saveThemeMode(mode)
            AppCompatDelegate.setDefaultNightMode(when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
            // 立即重建Activity应用新主题
            requireActivity().recreate()
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

    private fun doLocalBackup() {
        binding.btnLocalBackup.isEnabled = false
        binding.tvSyncStatus.text = "⏳ 本地备份中..."
        BackupManager.localBackupOnly(requireContext()) { success, message ->
            requireActivity().runOnUiThread {
                binding.btnLocalBackup.isEnabled = true
                binding.tvSyncStatus.text = if (success) "✅ $message" else "❌ $message"
                updateBackupStatus()
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