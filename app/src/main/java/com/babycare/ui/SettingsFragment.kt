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

    // 音频文件选择器：让用户选择自定义铃声
    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pickRingtoneFromUri(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadBackupConfig()
        loadThemeConfig()
        loadNotifyConfig()
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

        // ─── 通知设置 ───
        binding.btnPickRingtone.setOnClickListener { pickRingtone() }
        binding.btnResetRingtone.setOnClickListener { resetRingtone() }
        binding.btnSaveVibrate.setOnClickListener { saveVibrateSettings() }
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

    // ═══════════════════ 通知设置 ═══════════════════

    /** 加载通知配置到UI */
    private fun loadNotifyConfig() {
        // 铃声
        val audioPath = settings.getCustomAudioPath()
        if (audioPath != null) {
            val name = audioPath.substringAfterLast('/').ifEmpty { "自定义铃声" }
            binding.tvCurrentRingtone.text = name
        } else {
            binding.tvCurrentRingtone.text = "默认铃声"
        }
        // 音频播报重复次数
        binding.etAudioRepeatCount.setText(settings.getAudioRepeatCount().toString())
        // 震动参数
        binding.etVibrateDuration.setText(settings.getVibrateDuration().toString())
        binding.etVibrateInterval.setText(settings.getVibrateInterval().toString())
    }

    /** 打开系统文件选择器，让用户选取音频文件作为自定义铃声 */
    private fun pickRingtone() {
        audioPicker.launch(arrayOf("audio/*", "*/*"))
    }

    /** 将用户选择的音频复制到应用内部存储，并保存路径 */
    private fun pickRingtoneFromUri(uri: Uri) {
        binding.tvNotifyStatus.text = "⏳ 正在复制铃声..."
        try {
            val input = requireContext().contentResolver.openInputStream(uri) ?: return
            val dir = java.io.File(requireContext().filesDir, "ringtones")
            dir.mkdirs()
            val dest = java.io.File(dir, "custom_ringtone_${System.currentTimeMillis()}.mp3")
            input.use { inp ->
                dest.outputStream().use { out ->
                    inp.copyTo(out)
                }
            }
            val savedPath = dest.absolutePath
            settings.saveCustomAudioPath(savedPath)
            binding.tvCurrentRingtone.text = dest.name
            binding.tvNotifyStatus.text = "✅ 已设为自定义铃声: ${dest.name}"
        } catch (e: Exception) {
            binding.tvNotifyStatus.text = "❌ 设置铃声失败: ${e.message}"
        }
    }

    /** 恢复默认铃声 */
    private fun resetRingtone() {
        settings.saveCustomAudioPath(null)
        binding.tvCurrentRingtone.text = "默认铃声"
        binding.tvNotifyStatus.text = "✅ 已恢复默认铃声"
    }

    /** 保存震动与音频设置 */
    private fun saveVibrateSettings() {
        val durText = binding.etVibrateDuration.text.toString()
        val intText = binding.etVibrateInterval.text.toString()
        if (durText.isBlank() || intText.isBlank()) {
            binding.tvNotifyStatus.text = "⚠️ 请填写震动时长和间隔时间"
            return
        }
        var dur = durText.toLongOrNull()
        var interval = intText.toLongOrNull()
        if (dur == null || dur < 0) { binding.etVibrateDuration.error = "无效数字"; return }
        if (interval == null || interval < 0) { binding.etVibrateInterval.error = "无效数字"; return }
        // 确保最小值
        if (dur == 0L) dur = 1L
        if (interval == 0L) interval = 1L
        settings.saveVibrateDuration(dur)
        settings.saveVibrateInterval(interval)

        // 音频播报重复次数
        val repeatText = binding.etAudioRepeatCount.text.toString()
        if (repeatText.isNotBlank()) {
            val count = repeatText.toIntOrNull()
            if (count != null && count > 0) {
                settings.saveAudioRepeatCount(count)
            } else {
                binding.etAudioRepeatCount.error = "请输入大于0的数字"
                return
            }
        }

        binding.tvNotifyStatus.text = "✅ 设置已保存（震动${dur}ms, 间隔${interval}ms, 重复${settings.getAudioRepeatCount()}次）"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}