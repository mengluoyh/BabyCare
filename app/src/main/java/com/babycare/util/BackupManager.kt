// BabyCare/app/src/main/java/com/babycare/util/BackupManager.kt
package com.babycare.util

import android.content.Context
import com.babycare.BabyCareApp
import com.babycare.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 统一备份管理器：同时支持本地备份 + WebDAV备份
 * 备份格式为纯文本TXT，每行一条记录，易于阅读和手动编辑
 * 格式示例:
 *   2026-07-05 08:05:31 配方奶|60ml
 *   2026-07-05 06:00:00 母乳
 *   2026-07-05 09:30:00 大便|正常
 */
object BackupManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val TEXT_MEDIA = "text/plain; charset=utf-8".toMediaType()

    private val SDF = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** 执行完整备份（本地+WebDAV），通过回调报告结果 */
    fun backupAll(context: Context, onResult: (Boolean, String) -> Unit) {
        val settings = SettingsManager(context)
        val url = settings.getWebDavUrl()

        Thread {
            try {
                val data = fetchAllData(context)
                val results = mutableListOf<String>()
                var allSuccess = true

                // 1. 本地备份
                try {
                    val localFile = localBackup(context, data)
                    results.add("本地:${localFile.name}")
                } catch (e: Exception) {
                    results.add("本地失败:${e.message}")
                    allSuccess = false
                }

                // 2. WebDAV备份（如果配置了地址）
                if (url.isNotBlank()) {
                    try {
                        val filename = webdavBackup(context, data)
                        results.add("云端:${filename}")
                    } catch (e: Exception) {
                        results.add("云端失败:${e.message}")
                    }
                }

                settings.saveLastWebDavSyncTime(System.currentTimeMillis())
                val msg = results.joinToString(" | ")
                onResult(allSuccess, msg)
            } catch (e: Exception) {
                onResult(false, "备份失败:${e.message}")
            }
        }.start()
    }

    /** 仅本地备份 */
    fun localBackupOnly(context: Context, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val data = fetchAllData(context)
                val file = localBackup(context, data)
                onResult(true, "本地备份成功:${file.name}")
            } catch (e: Exception) {
                onResult(false, "本地备份失败:${e.message}")
            }
        }.start()
    }

    /** 仅WebDAV备份 */
    fun webdavBackupOnly(context: Context, onResult: (Boolean, String) -> Unit) {
        val settings = SettingsManager(context)
        if (settings.getWebDavUrl().isBlank()) {
            onResult(false, "WebDAV地址未设置")
            return
        }
        Thread {
            try {
                val data = fetchAllData(context)
                val filename = webdavBackup(context, data)
                settings.saveLastWebDavSyncTime(System.currentTimeMillis())
                onResult(true, "WebDAV备份成功:$filename")
            } catch (e: Exception) {
                onResult(false, "WebDAV备份失败:${e.message}")
            }
        }.start()
    }

    // ═══════════════════ 内部方法 ═══════════════════

    private fun fetchAllData(context: Context): BackupData {
        return runBlocking(Dispatchers.IO) {
            val db = (context.applicationContext as BabyCareApp).database
            BackupData(
                feedingRecords = db.feedingDao().getFeedingsBetween(0, Long.MAX_VALUE),
                excreteRecords = db.excreteDao().getExcretesBetween(0, Long.MAX_VALUE),
                babyProfile = db.babyDao().getProfileSync(),
                backupTime = System.currentTimeMillis()
            )
        }
    }

    /** 生成纯文本备份内容 */
    private fun generateTxtContent(data: BackupData): String {
        val sb = StringBuilder()
        sb.appendLine("=== 宝宝护理助手 - 数据备份 ===")
        sb.appendLine("备份时间: ${SDF.format(Date(data.backupTime))}")
        sb.appendLine()

        // 喂养记录
        sb.appendLine("--- 喂养记录 ---")
        if (data.feedingRecords.isEmpty()) {
            sb.appendLine("(无)")
        } else {
            for (r in data.feedingRecords) {
                val time = SDF.format(Date(r.timestamp))
                val type = if (r.feedType == "breast") "母乳" else "配方奶"
                val vol = if (r.volume != null) "|${r.volume}ml" else ""
                sb.appendLine("$time $type$vol")
            }
        }
        sb.appendLine()

        // 排泄记录
        sb.appendLine("--- 排泄记录 ---")
        if (data.excreteRecords.isEmpty()) {
            sb.appendLine("(无)")
        } else {
            for (r in data.excreteRecords) {
                val time = SDF.format(Date(r.timestamp))
                val type = if (r.type == "bowel") "大便" else "小便"
                val state = r.state?.let { "|${it}" } ?: ""
                sb.appendLine("$time $type$state")
            }
        }
        sb.appendLine()

        // 宝宝信息
        sb.appendLine("--- 宝宝信息 ---")
        data.babyProfile?.let { p ->
            sb.appendLine("出生日期: ${SDF.format(Date(p.birthDate))}")
            sb.appendLine("体重: ${p.weight}kg")
        } ?: sb.appendLine("(未设置)")

        return sb.toString()
    }

    private fun localBackup(context: Context, data: BackupData): File {
        val dir = File("/storage/emulated/0/BabyCare/backups")
        if (!dir.exists()) dir.mkdirs()

        val txt = generateTxtContent(data)
        val filename = "babycare_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val file = File(dir, filename)
        file.writeText(txt)
        return file
    }

    private fun webdavBackup(context: Context, data: BackupData): String {
        val settings = SettingsManager(context)
        val url = settings.getWebDavUrl()
        val user = settings.getWebDavUser()
        val pass = settings.getWebDavPass()

        val txt = generateTxtContent(data)
        val filename = "babycare_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val requestUrl = "${url.trimEnd('/')}/$filename"
        val credential = Credentials.basic(user, pass)
        val body = txt.toRequestBody(TEXT_MEDIA)

        val request = Request.Builder()
            .url(requestUrl)
            .put(body)
            .header("Authorization", credential)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code} ${response.message}")
            }
        }
        return filename
    }

    /** 从纯文本恢复数据（逐行解析备份文件） */
    fun restore(context: Context, txtContent: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val feedings = mutableListOf<FeedingRecord>()
                val excretes = mutableListOf<ExcreteRecord>()
                val lines = txtContent.lines()
                var section = ""
                val dateSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("---") -> {
                            section = trimmed.replace("-", "").trim()
                        }
                        trimmed.isEmpty() || trimmed.startsWith("=") || trimmed.startsWith("备份") || trimmed.startsWith("(") -> { /* skip */ }
                        else -> {
                            when (section) {
                                "喂养记录" -> {
                                    // 格式: "2026-07-05 08:05:31 配方奶|60ml" 或 "2026-07-05 06:00:00 母乳"
                                    val parts = trimmed.split(" ", limit = 2)
                                    if (parts.size >= 2) {
                                        try {
                                            val ts = dateSdf.parse(parts[0])?.time ?: continue
                                            val rest = parts[1]
                                            if (rest.startsWith("母乳")) {
                                                feedings.add(FeedingRecord(timestamp = ts, type = "manual", feedType = "breast"))
                                            } else if (rest.startsWith("配方奶")) {
                                                val ml = rest.substringAfter("|").removeSuffix("ml").toIntOrNull()
                                                feedings.add(FeedingRecord(timestamp = ts, type = "manual", feedType = "formula", volume = ml))
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                                "排泄记录" -> {
                                    // 格式: "2026-07-05 09:30:00 大便|正常"
                                    val parts = trimmed.split(" ", limit = 2)
                                    if (parts.size >= 2) {
                                        try {
                                            val ts = dateSdf.parse(parts[0])?.time ?: continue
                                            val rest = parts[1]
                                            val type = if (rest.startsWith("大便")) "bowel" else "pee"
                                            val state = rest.substringAfter("|", "")
                                            excretes.add(ExcreteRecord(timestamp = ts, type = type, state = state.ifBlank { null }))
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }

                runBlocking(Dispatchers.IO) {
                    val db = (context.applicationContext as BabyCareApp).database
                    if (feedings.isNotEmpty()) db.feedingDao().insertAll(feedings)
                    if (excretes.isNotEmpty()) db.excreteDao().insertAll(excretes)
                }
                onResult(true, "恢复成功:${feedings.size}条喂养,${excretes.size}条排泄")
            } catch (e: Exception) {
                onResult(false, "恢复失败:${e.message}")
            }
        }.start()
    }

    // ═══════════════════ 数据类（内部使用） ═══════════════════
    data class BackupData(
        val feedingRecords: List<FeedingRecord> = emptyList(),
        val excreteRecords: List<ExcreteRecord> = emptyList(),
        val babyProfile: BabyProfile? = null,
        val backupTime: Long = System.currentTimeMillis()
    )
}