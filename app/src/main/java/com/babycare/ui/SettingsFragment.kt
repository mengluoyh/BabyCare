// BabyCare/app/src/main/java/com/babycare/ui/SettingsFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.R
import com.babycare.data.SettingsManager
import com.babycare.databinding.FragmentSettingsBinding
import com.babycare.util.BackupManager
import com.babycare.util.IconChanger
import com.babycare.util.WebDavManager
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val settings by lazy { SettingsManager(requireContext()) }

    // 文件选择器：让用户从任意文件夹选择 .json 备份文件
    private val backupFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) restoreFromUri(uri)
    }

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
        val files = BackupManager.listBackupFiles()
        binding.tvLocalBackupCount.text = if (files.isNotEmpty()) "本地备份文件: ${files.size} 个" else "暂无本地备份"
        binding.tvSyncStatus.text = ""
        binding.btnRestoreBackup.isEnabled = files.isNotEmpty()
    }

    private fun setupUI() {
        // ─── 本地备份 ───
        binding.btnLocalBackup.setOnClickListener { doLocalBackup() }
        binding.btnRestoreBackup.setOnClickListener { showRestorePicker() }
        binding.btnPickBackupFile.setOnClickListener { pickBackupFile() }

        // ─── WebDAV 远程备份 ───
        loadWebDavConfig()
        binding.btnWebDavSave.setOnClickListener { saveWebDavConfig() }
        binding.btnWebDavUpload.setOnClickListener { uploadToWebDav() }
        binding.btnWebDavDownload.setOnClickListener { downloadFromWebDav() }

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
        binding.tvSyncStatus.text = "⏳ 备份中..."
        lifecycleScope.launch {
            val result = BackupManager.backupAll(requireContext())
            binding.btnLocalBackup.isEnabled = true
            result.onSuccess { msg ->
                binding.tvSyncStatus.text = "✅ $msg"
            }.onFailure { e ->
                binding.tvSyncStatus.text = "❌ 备份失败:${e.message}"
            }
            updateBackupStatus()
        }
    }

    private fun showRestorePicker() {
        val files = BackupManager.listBackupFiles()
        if (files.isEmpty()) {
            Toast.makeText(requireContext(), "没有可恢复的备份", Toast.LENGTH_SHORT).show()
            return
        }
        val names = files.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择备份文件恢复")
            .setItems(names) { _, which ->
                doRestore(files[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRestore(file: java.io.File) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认恢复")
            .setMessage("恢复操作会添加备份中的记录到现有数据中，确定继续？")
            .setPositiveButton("确定") { _, _ ->
                binding.tvSyncStatus.text = "⏳ 恢复中..."
                lifecycleScope.launch {
                    val result = BackupManager.restoreFromFile(file)
                    result.onSuccess { msg ->
                        binding.tvSyncStatus.text = "✅ $msg"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }.onFailure { e ->
                        binding.tvSyncStatus.text = "❌ 恢复失败:${e.message}"
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 打开系统文件选择器，让用户选取任意 .json 备份文件 */
    private fun pickBackupFile() {
        backupFilePicker.launch(arrayOf("application/json", "*/*"))
    }

    /** 从用户选择的 URI 读取并恢复备份 */
    private fun restoreFromUri(uri: Uri) {
        binding.tvSyncStatus.text = "⏳ 恢复中..."
        lifecycleScope.launch {
            try {
                val input = requireContext().contentResolver.openInputStream(uri)
                val json = input?.bufferedReader()?.use { it.readText() } ?: ""
                if (json.isBlank()) {
                    binding.tvSyncStatus.text = "❌ 文件为空或无法读取"
                    return@launch
                }
                val data = com.google.gson.Gson().fromJson(json, BackupManager.BackupData::class.java)
                val app = com.babycare.BabyCareApp.instance
                val db = app.database
                if (data.feedingRecords.isNotEmpty()) db.feedingDao().insertAll(data.feedingRecords)
                if (data.excreteRecords.isNotEmpty()) db.excreteDao().insertAll(data.excreteRecords)
                data.babyProfile?.let { db.babyDao().upsertProfile(it) }
                val msg = "恢复成功:${data.feedingRecords.size}条喂养,${data.excreteRecords.size}条排泄"
                binding.tvSyncStatus.text = "✅ $msg"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.tvSyncStatus.text = "❌ 恢复失败:${e.message}"
            }
        }
    }

    private fun loadWebDavConfig() {
        val config = WebDavManager.loadConfig(requireContext())
        if (config != null) {
            binding.etWebDavUrl.setText(config.url)
            binding.etWebDavUser.setText(config.username)
            binding.etWebDavPassword.setText(config.password)
        }
    }

    private fun saveWebDavConfig() {
        val url = binding.etWebDavUrl.text.toString().trim()
        if (url.isBlank()) {
            binding.tvWebDavStatus.text = "⚠️ 请输入服务器地址"
            return
        }
        val username = binding.etWebDavUser.text.toString().trim()
        val password = binding.etWebDavPassword.text.toString().trim()
        WebDavManager.saveConfig(requireContext(), url, username, password)
        binding.tvWebDavStatus.text = "✅ 配置已保存"
    }

    private fun uploadToWebDav() {
        binding.btnWebDavUpload.isEnabled = false
        binding.tvWebDavStatus.text = "⏳ 上传中..."
        lifecycleScope.launch {
            val result = WebDavManager.upload(requireContext())
            binding.btnWebDavUpload.isEnabled = true
            result.onSuccess { msg ->
                binding.tvWebDavStatus.text = "✅ $msg"
            }.onFailure { e ->
                binding.tvWebDavStatus.text = "❌ ${e.message}"
            }
        }
    }

    private fun downloadFromWebDav() {
        binding.btnWebDavDownload.isEnabled = false
        binding.tvWebDavStatus.text = "⏳ 下载恢复中..."
        lifecycleScope.launch {
            val result = WebDavManager.downloadAndRestore(requireContext())
            binding.btnWebDavDownload.isEnabled = true
            result.onSuccess { msg ->
                binding.tvWebDavStatus.text = "✅ $msg"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                binding.tvWebDavStatus.text = "❌ ${e.message}"
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