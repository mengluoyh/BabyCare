// BabyCare/app/src/main/java/com/babycare/util/BackupManager.kt
package com.babycare.util

import android.content.Context
import com.babycare.BabyCareApp
import com.babycare.data.*
import com.google.gson.Gson
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
 */
object BackupManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json".toMediaType()

    data class BackupData(
        val feedingRecords: List<FeedingRecord> = emptyList(),
        val excreteRecords: List<ExcreteRecord> = emptyList(),
        val babyProfile: BabyProfile? = null,
        val backupTime: Long = System.currentTimeMillis()
    )

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
                        // WebDAV失败不阻断全部
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

    private fun localBackup(context: Context, data: BackupData): File {
        val dir = File("/storage/emulated/0/BabyCare/backups")
        if (!dir.exists()) dir.mkdirs()

        val json = Gson().toJson(data)
        val filename = "babycare_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        val file = File(dir, filename)
        file.writeText(json)
        return file
    }

    private fun webdavBackup(context: Context, data: BackupData): String {
        val settings = SettingsManager(context)
        val url = settings.getWebDavUrl()
        val user = settings.getWebDavUser()
        val pass = settings.getWebDavPass()

        val json = Gson().toJson(data)
        val filename = "babycare_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        val requestUrl = "${url.trimEnd('/')}/$filename"
        val credential = Credentials.basic(user, pass)
        val body = json.toRequestBody(JSON_MEDIA)

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

    /** 从JSON恢复数据 */
    fun restore(context: Context, jsonData: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val data = Gson().fromJson(jsonData, BackupData::class.java)
                runBlocking(Dispatchers.IO) {
                    val db = (context.applicationContext as BabyCareApp).database
                    db.feedingDao().insertAll(data.feedingRecords)
                    db.excreteDao().insertAll(data.excreteRecords)
                    data.babyProfile?.let { db.babyDao().upsertProfile(it) }
                }
                onResult(true, "恢复成功:${data.feedingRecords.size}条喂养,${data.excreteRecords.size}条排泄")
            } catch (e: Exception) {
                onResult(false, "恢复失败:${e.message}")
            }
        }.start()
    }
}