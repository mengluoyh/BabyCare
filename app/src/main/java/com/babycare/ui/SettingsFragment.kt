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
        loadBackupConfig()
        loadThemeConfig()
        loadIconConfig()
        loadColorConfig()
        setupUI()
        setupColorUI()
    }

    // ═══════════════════ 备份 ═══════════════════

    private fun loadBackupConfig() {
        // WebDAV
        binding.etWebDavUrl.setText(settings.getWebDavUrl())
        binding.etWebDavUser.setText(settings.getWebDavUser())
        binding.etWebDavPass.setText(settings.getWebDavPass())
        binding.switchAutoSync.isChecked = settings.isWebDavAutoSync()
        updateBackupStatus()
    }

    private fun updateBackupStatus() {
        val lastSync = settings.getLastWebDavSyncTime()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val text = if (lastSync > 0) "上次备份: ${sdf.format(Date(lastSync))}" else "尚未备份"
        binding.tvSyncStatus.text = text

        // 列出本地备份文件数
        val localDir = java.io.File("/storage/emulated/0/BabyCare/backups")
        val count = if (localDir.exists()) localDir.listFiles()?.filter { it.name.startsWith("babycare_backup_") && it.name.endsWith(".json") }?.size ?: 0 else 0
        binding.tvLocalBackupCount.text = if (count > 0) "本地备份文件: $count 个" else "暂无本地备份"
    }

    private fun setupUI() {
        // ─── 备份 ───
        binding.btnWebDavSave.setOnClickListener { saveWebDavConfig() }
        binding.btnBackupNow.setOnClickListener { doBackupAll() }
        binding.btnLocalBackup.setOnClickListener { doLocalBackup() }
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

        Toast.makeText(requireContext(), "配置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun doBackupAll() {
        binding.btnBackupNow.isEnabled = false
        binding.tvSyncStatus.text = "⏳ 正在备份..."
        BackupManager.backupAll(requireContext()) { success, message ->
            requireActivity().runOnUiThread {
                binding.btnBackupNow.isEnabled = true
                binding.tvSyncStatus.text = if (success) "✅ $message" else "❌ $message"
                updateBackupStatus()
            }
        }
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

    // ═══════════════════ 颜色自定义 ═══════════════════

    /** 6种配色方案: schemeName -> (layoutBg, fontColor, chartColor) */
    private val colorSchemes = mapOf(
        "orange" to Triple("#FFF8F0", "#201A17", "#FF6B35"),
        "blue" to Triple("#F0F8FF", "#1A1A2E", "#1976D2"),
        "pink" to Triple("#FFF0F5", "#2E1A1A", "#D94A6A"),
        "green" to Triple("#F0FFF0", "#1A2E1A", "#4AD98A"),
        "purple" to Triple("#F5F0FF", "#1A1A2E", "#7B61FF"),
        "teal" to Triple("#F0FFFF", "#1A2E2E", "#00BCD4")
    )

    private fun loadColorConfig() {
        val scheme = settings.getColorScheme()
        updateColorSchemeUI(scheme)
    }

    private fun setupColorUI() {
        binding.rgColorScheme.setOnCheckedChangeListener { _, checkedId ->
            val scheme = when (checkedId) {
                R.id.rbColorOrange -> "orange"
                R.id.rbColorBlue -> "blue"
                R.id.rbColorPink -> "pink"
                R.id.rbColorGreen -> "green"
                R.id.rbColorPurple -> "purple"
                R.id.rbColorTeal -> "teal"
                else -> "orange"
            }
            applyColorScheme(scheme)
        }
    }

    private fun applyColorScheme(scheme: String) {
        val (layoutBg, fontColor, chartColor) = colorSchemes[scheme] ?: return
        settings.saveLayoutColor(layoutBg)
        settings.saveFontColor(fontColor)
        settings.saveChartColor(chartColor)
        settings.saveColorScheme(scheme)
        updateColorSchemeUI(scheme)
        Toast.makeText(requireContext(), "配色已切换为${getSchemeLabel(scheme)}，重新打开界面生效", Toast.LENGTH_SHORT).show()
    }

    private fun updateColorSchemeUI(scheme: String) {
        val label = getSchemeLabel(scheme)
        binding.tvCurrentColorScheme.text = "当前：$label"
        val rbId = when (scheme) {
            "blue" -> R.id.rbColorBlue
            "pink" -> R.id.rbColorPink
            "green" -> R.id.rbColorGreen
            "purple" -> R.id.rbColorPurple
            "teal" -> R.id.rbColorTeal
            else -> R.id.rbColorOrange
        }
        binding.rgColorScheme.check(rbId)
    }

    private fun getSchemeLabel(scheme: String): String = when (scheme) {
        "blue" -> "🔵 清新蓝"
        "pink" -> "🩷 甜美粉"
        "green" -> "🟢 自然绿"
        "purple" -> "🟣 优雅紫"
        "teal" -> "🔷 海洋青"
        else -> "🟠 温暖橙"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}