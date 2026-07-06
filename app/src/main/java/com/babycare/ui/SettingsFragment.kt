// BabyCare/app/src/main/java/com/babycare/ui/SettingsFragment.kt
package com.babycare.ui

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
import com.babycare.data.SettingsManager
import com.babycare.databinding.FragmentSettingsBinding
import com.babycare.util.BackupManager
import com.babycare.util.WebDavManager
import kotlinx.coroutines.launch

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
    }

    private fun setupUI() {
        // ─── 本地备份 ───
        binding.btnLocalBackup.setOnClickListener { doLocalBackup() }
        binding.btnRestoreBackup.setOnClickListener { pickBackupFile() }
        binding.btnSyncAll.setOnClickListener { syncAll() }

        // ─── WebDAV 远程备份 ───
        loadWebDavConfig()
        binding.btnWebDavSave.setOnClickListener { saveWebDavConfig() }
        binding.btnWebDavUpload.setOnClickListener { uploadToWebDav() }
        binding.btnWebDavDownload.setOnClickListener { downloadFromWebDav() }

        // ─── 主题 ───
        binding.rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.rbThemeLight.id -> "light"
                binding.rbThemeDark.id -> "dark"
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

        // ─── 音频播报次数 ───
        binding.etAudioRepeatCount.setText(settings.getAudioRepeatCount().toString())
        binding.btnSaveAudioRepeat.setOnClickListener {
            val count = binding.etAudioRepeatCount.text.toString().toIntOrNull()
            if (count != null && count in 1..10) {
                settings.saveAudioRepeatCount(count)
                Toast.makeText(requireContext(), "音频播报次数已保存: $count 次", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "请输入1~10之间的数字", Toast.LENGTH_SHORT).show()
            }
        }
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

    /** 打开系统文件选择器，让用户选取任意 .json 备份文件恢复 */
    private fun pickBackupFile() {
        backupFilePicker.launch(arrayOf("application/json", "*/*"))
    }

    /** 本地备份 + WebDAV 同步同时执行 */
    private fun syncAll() {
        binding.btnSyncAll.isEnabled = false
        binding.tvSyncStatus.text = "⏳ 本地备份 + 远程同步中..."
        lifecycleScope.launch {
            val localResult = BackupManager.backupAll(requireContext())
            val webdavResult = WebDavManager.upload(requireContext())

            val localOk = localResult.isSuccess
            val remoteOk = webdavResult.isSuccess

            if (localOk && remoteOk) {
                binding.tvSyncStatus.text = "✅ 本地备份 + 远程同步备份成功"
                Toast.makeText(requireContext(), "✅ 本地备份 + 远程同步备份成功", Toast.LENGTH_LONG).show()
            } else {
                val sb = StringBuilder()
                localResult.onSuccess { sb.append("✅ 本地$it") }
                    .onFailure { sb.append("❌ 本地备份失败:${it.message}") }
                sb.append(" | ")
                webdavResult.onSuccess { sb.append("✅ 远程$it") }
                    .onFailure { sb.append("❌ 远程同步失败:${it.message}") }
                binding.tvSyncStatus.text = sb.toString()
            }
            updateBackupStatus()
            binding.btnSyncAll.isEnabled = true
        }
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
                val data = com.google.gson.Gson().fromJson(json, com.babycare.data.BackupData::class.java)
                val app = com.babycare.BabyCareApp.instance
                val db = app.database
                if (data.feedingRecords.isNotEmpty()) db.feedingDao().insertAll(data.feedingRecords)
                if (data.excreteRecords.isNotEmpty()) db.excreteDao().insertAll(data.excreteRecords)
                data.babyProfile?.let { db.babyDao().upsertProfile(it) }
                if (data.weightRecords.isNotEmpty()) data.weightRecords.forEach { db.weightDao().insert(it) }
                if (data.vaccinationRecords.isNotEmpty()) db.vaccineDao().insertAll(data.vaccinationRecords)
                val msg = "恢复成功:${data.feedingRecords.size}条喂养,${data.excreteRecords.size}条排泄,${data.weightRecords.size}条体重,${data.vaccinationRecords.size}条疫苗"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}