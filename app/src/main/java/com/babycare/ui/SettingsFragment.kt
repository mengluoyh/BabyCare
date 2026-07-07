// BabyCare/app/src/main/java/com/babycare/ui/SettingsFragment.kt
package com.babycare.ui

import android.media.MediaPlayer
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
import com.babycare.util.SyncEngine
import com.babycare.util.SyncWorker
import com.babycare.util.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val settings by lazy { SettingsManager(requireContext()) }

    // 文件选择器：让用户从任意文件夹选择 .json 备份文件
    private val backupFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) restoreFromUri(uri)
    }

    // 音频文件选择器：从文件夹选择播报音频
    private val audioFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            settings.saveAudioFilePath(uri.toString())
            updateAudioFileDisplay()
            Toast.makeText(requireContext(), "✅ 播报音频已选择", Toast.LENGTH_SHORT).show()
        }
    }

    // 背景图片选择器：选择APP背景图
    private val backgroundImagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            settings.saveBackgroundImagePath(uri.toString())
            updateBackgroundPreview()
            applyBackgroundToActivity()
            Toast.makeText(requireContext(), "✅ 背景图已选择", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadBackupConfig()
        loadThemeConfig()
        loadBackgroundConfig()
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

        // ─── 增量同步 ───
        binding.btnSyncNow.setOnClickListener { doIncrementalSync() }
        updateLastSyncDisplay()

        // ─── 自动同步 ───
        loadAutoSyncConfig()
        binding.swAutoSync.setOnCheckedChangeListener { _, checked ->
            settings.setAutoSyncEnabled(checked)
            binding.etAutoSyncInterval.isEnabled = checked
            if (checked) {
                val interval = binding.etAutoSyncInterval.text.toString().toIntOrNull() ?: 6
                settings.setAutoSyncInterval(interval)
            }
            SyncWorker.schedule(requireContext())
        }
        binding.etAutoSyncInterval.setOnEditorActionListener { _, _, _ ->
            val interval = binding.etAutoSyncInterval.text.toString().toIntOrNull()
            if (interval != null && interval in 1..24) {
                settings.setAutoSyncInterval(interval)
                if (settings.isAutoSyncEnabled()) SyncWorker.schedule(requireContext())
            }
            true
        }

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

        // ─── 音频文件选择 ───
        updateAudioFileDisplay()
        binding.btnSelectAudio.setOnClickListener {
            audioFilePicker.launch(arrayOf("audio/*", "*/*"))
        }
        binding.btnTestAudio.setOnClickListener { testAudioPlay() }

        // ─── 背景图 ───
        binding.btnSelectBackground.setOnClickListener {
            backgroundImagePicker.launch(arrayOf("image/*"))
        }
        binding.btnClearBackground.setOnClickListener {
            settings.saveBackgroundImagePath("")
            updateBackgroundPreview()
            applyBackgroundToActivity()
            Toast.makeText(requireContext(), "🗑️ 背景图已清除", Toast.LENGTH_SHORT).show()
        }
        binding.sbBackgroundAlpha.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvBackgroundAlphaValue.text = "${(progress * 100 / 255)}%"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                settings.saveBackgroundAlpha(seekBar?.progress ?: 255)
                applyBackgroundToActivity()
            }
        })
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

    // ═══════════════════ 增量同步 ═══════════════════

    private fun doIncrementalSync() {
        binding.btnSyncNow.isEnabled = false
        binding.tvSyncStatus.text = "⏳ 增量同步中..."
        lifecycleScope.launch {
            val result = SyncEngine.sync(requireContext())
            binding.btnSyncNow.isEnabled = true
            result.onSuccess { summary ->
                val msg = "✅ 同步完成 ↑推送${summary.pushed} ↓拉取${summary.pulled}"
                binding.tvSyncStatus.text = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                binding.tvSyncStatus.text = "❌ 同步失败:${e.message}"
            }
            updateLastSyncDisplay()
        }
    }

    private fun updateLastSyncDisplay() {
        val lastSync = SyncEngine.getLastSyncTime(requireContext())
        binding.tvWebDavStatus.text = if (lastSync > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            "上次同步: ${sdf.format(Date(lastSync))}"
        } else {
            "尚未同步"
        }
    }

    // ═══════════════════ 自动同步 ═══════════════════

    private fun loadAutoSyncConfig() {
        binding.swAutoSync.isChecked = settings.isAutoSyncEnabled()
        binding.etAutoSyncInterval.setText(settings.getAutoSyncInterval().toString())
        binding.etAutoSyncInterval.isEnabled = settings.isAutoSyncEnabled()
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

    // ═══════════════════ 背景图 ═══════════════════

    private fun loadBackgroundConfig() {
        val path = settings.getBackgroundImagePath()
        val alpha = settings.getBackgroundAlpha()
        binding.sbBackgroundAlpha.progress = alpha
        binding.tvBackgroundAlphaValue.text = "${(alpha * 100 / 255)}%"
        updateBackgroundPreview()
    }

    private fun updateBackgroundPreview() {
        val path = settings.getBackgroundImagePath()
        if (path.isEmpty()) {
            binding.ivBackgroundPreview.visibility = android.view.View.GONE
            return
        }
        try {
            val uri = android.net.Uri.parse(path)
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                binding.ivBackgroundPreview.setImageBitmap(bitmap)
                binding.ivBackgroundPreview.visibility = android.view.View.VISIBLE
            }
        } catch (_: Exception) {
            binding.ivBackgroundPreview.visibility = android.view.View.GONE
        }
    }

    private fun applyBackgroundToActivity() {
        (requireActivity() as? com.babycare.MainActivity)?.applyBackground()
    }

    // ═══════════════════ 音频文件 ═══════════════════

    /** 更新音频文件名显示 */
    private fun updateAudioFileDisplay() {
        val path = settings.getAudioFilePath()
        if (path.isEmpty()) {
            binding.tvAudioFileName.text = "未选择"
            return
        }
        try {
            val uri = Uri.parse(path)
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        binding.tvAudioFileName.text = it.getString(nameIdx)
                        return
                    }
                }
            }
        } catch (_: Exception) { }
        binding.tvAudioFileName.text = "已选择音频"
    }

    /** 试听当前选择的音频 */
    private fun testAudioPlay() {
        val path = settings.getAudioFilePath()
        if (path.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择音频文件", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(requireContext(), "▶️ 试听中...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                var player: MediaPlayer? = null
                try {
                    val uri = Uri.parse(path)
                    player = MediaPlayer().apply {
                        setDataSource(requireContext(), uri)
                        prepare()
                        start()
                    }
                    // 播放3秒后停止（试听）
                    kotlinx.coroutines.delay(3000)
                } catch (_: Exception) { }
                finally { player?.release() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}