// BabyCare/app/src/main/java/com/babycare/util/SyncEngine.kt
package com.babycare.util

import android.content.Context
import android.util.Base64
import com.babycare.BabyCareApp
import com.babycare.data.*
import com.babycare.util.WebDavManager.WebDavConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * WebDAV 增量同步引擎。
 *
 * 原理：
 * 1. 每台设备有唯一 deviceId（首次生成后持久化）。
 * 2. 每次同步，收集本地 lastModified > 上次同步时间 的增量变更。
 * 3. 上传增量变更文件到 WebDAV（babycare_sync/ 目录）。
 * 4. 下载其他设备上传的、时间 > 上次同步时间的变更文件。
 * 5. 按 Last-Write-Wins 策略合并到本地 DB。
 * 6. 清理已处理的远程变更文件。
 */
object SyncEngine {

    private const val SYNC_DIR = "babycare_sync"
    private const val PREF_NAME = "sync_prefs"
    private const val KEY_DEVICE_ID = "sync_device_id"
    private const val KEY_LAST_SYNC = "sync_last_sync_time"
    private const val TIMEOUT = 15000
    private val gson = Gson()

    // ── 设备标识 ──

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    fun getLastSyncTime(context: Context): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)
    }

    fun setLastSyncTime(context: Context, time: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_SYNC, time).apply()
    }

    // ── 同步入口 ──

    /** 最大重试次数 */
    private const val MAX_RETRIES = 3
    /** 批量处理每批最大记录数 */
    private const val BATCH_SIZE = 50

    suspend fun sync(context: Context): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val config = WebDavManager.loadConfig(context)
                ?: return@withContext Result.failure(Exception("未配置 WebDAV"))
            if (config.url.isBlank()) {
                return@withContext Result.failure(Exception("WebDAV 地址为空"))
            }

            val deviceId = getDeviceId(context)
            val lastSync = getLastSyncTime(context)
            val now = System.currentTimeMillis()
            val errors = mutableListOf<String>()

            // 1. 推送本地增量（带重试）
            val pushed = retryOnFailure(MAX_RETRIES) {
                pushLocalChanges(context, config, deviceId, lastSync)
            }

            // 2. 拉取远程增量并合并（带重试）
            val pulled = retryOnFailure(MAX_RETRIES) {
                pullRemoteChanges(context, config, deviceId, lastSync)
            }

            // 3. 记录同步时间
            setLastSyncTime(context, now)

            Result.success(SyncSummary(pushed = pushed, pulled = pulled, errors = errors))
        } catch (e: Exception) {
            android.util.Log.e("SyncEngine", "同步失败", e)
            Result.failure(e)
        }
    }

    /** 带重试的异步操作 */
    private suspend fun <T> retryOnFailure(maxRetries: Int, block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("SyncEngine", "操作失败 (尝试 $attempt/$maxRetries): ${e.message}")
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(1000L * attempt) // 指数退避
                }
            }
        }
        throw lastException ?: Exception("重试耗尽")
    }

    // ── Push：上传本地增量 ──

    private suspend fun pushLocalChanges(
        context: Context, config: WebDavConfig, deviceId: String, since: Long
    ): Int {
        val changes = collectChanges(context, since)
        val total = changes.feedingRecords.size + changes.excreteRecords.size +
                changes.vaccinationRecords.size + changes.weightRecords.size +
                changes.babyProfile.size
        if (total == 0) return 0

        val payload = SyncPayload(
            deviceId = deviceId,
            syncTime = System.currentTimeMillis(),
            changes = changes
        )
        val json = gson.toJson(payload)
        val filename = "changes_${deviceId}_${System.currentTimeMillis()}.json"
        val baseUrl = config.url.trimEnd('/')
        val targetUrl = "$baseUrl/$SYNC_DIR/$filename"

        val conn = URL(targetUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doOutput = true
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.setRequestProperty("Authorization", basicAuth(config))
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            throw Exception("上传增量失败: HTTP $code")
        }
        return total
    }

    private suspend fun collectChanges(context: Context, since: Long): SyncChanges {
        val db = BabyCareApp.instance.database
        return SyncChanges(
            feedingRecords = db.feedingDao().getModifiedSince(since),
            excreteRecords = db.excreteDao().getModifiedSince(since),
            vaccinationRecords = db.vaccineDao().getModifiedSince(since),
            weightRecords = db.weightDao().getModifiedSince(since),
            babyProfile = db.babyDao().getModifiedSince(since)
        )
    }

    // ── Pull：下载远程增量并合并 ──

    private suspend fun pullRemoteChanges(
        context: Context, config: WebDavConfig, myDeviceId: String, since: Long
    ): Int {
        val files = listChangeFiles(config)
        // 过滤：非本设备的文件，且时间戳 > since
        val targetFiles = files.filter { name ->
            val deviceId = extractDeviceId(name)
            val ts = extractTimestamp(name)
            deviceId != myDeviceId && ts != null && ts > since
        }

        var totalPulled = 0
        for (file in targetFiles) {
            try {
                val json = httpGetFile(config, file)
                if (json == null) continue
                val payload = gson.fromJson(json, SyncPayload::class.java)
                mergeToLocal(context, payload)
                totalPulled += countChanges(payload.changes)
                // 处理完后删除远程文件（避免重复处理）
                deleteRemoteFile(config, file)
            } catch (e: Exception) {
                android.util.Log.w("SyncEngine", "处理远程文件 $file 失败: ${e.message}")
            }
        }
        return totalPulled
    }

    /** 合并远程增量到本地数据库（LWW） */
    private suspend fun mergeToLocal(context: Context, payload: SyncPayload) {
        val db = BabyCareApp.instance.database

        // 喂养记录
        for (remote in payload.changes.feedingRecords) {
            val local = db.feedingDao().getById(remote.id)
            if (local == null || remote.lastModified > local.lastModified) {
                if (remote.isDeleted) {
                    db.feedingDao().delete(remote)
                } else {
                    db.feedingDao().upsert(remote)
                }
            }
        }

        // 排泄记录
        for (remote in payload.changes.excreteRecords) {
            val local = db.excreteDao().getById(remote.id)
            if (local == null || remote.lastModified > local.lastModified) {
                if (remote.isDeleted) {
                    db.excreteDao().delete(remote)
                } else {
                    db.excreteDao().upsert(remote)
                }
            }
        }

        // 疫苗记录
        for (remote in payload.changes.vaccinationRecords) {
            val local = db.vaccineDao().getById(remote.id)
            if (local == null || remote.lastModified > local.lastModified) {
                if (remote.isDeleted) {
                    db.vaccineDao().delete(remote)
                } else {
                    db.vaccineDao().upsert(remote)
                }
            }
        }

        // 体重记录
        for (remote in payload.changes.weightRecords) {
            val local = db.weightDao().getById(remote.id)
            if (local == null || remote.lastModified > local.lastModified) {
                if (remote.isDeleted) {
                    db.weightDao().delete(remote)
                } else {
                    db.weightDao().upsert(remote)
                }
            }
        }

        // 宝宝档案（只有一个 id=1 的记录）
        for (remote in payload.changes.babyProfile) {
            val local = db.babyDao().getProfileSync()
            if (local == null || remote.lastModified > local.lastModified) {
                db.babyDao().upsertProfile(remote)
            }
        }
    }

    // ── 文件操作 ──

    /** 列出 WebDAV babycare_sync/ 目录下的所有变更文件 */
    private fun listChangeFiles(config: WebDavConfig): List<String> {
        val listUrl = config.url.trimEnd('/') + "/$SYNC_DIR/"
        val files = mutableListOf<String>()

        // 尝试 PROPFIND
        try {
            val conn = URL(listUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "PROPFIND"
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("Authorization", basicAuth(config))
            conn.setRequestProperty("Depth", "1")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val hrefRegex = Regex("<(?:d:)?href>(.*?)</(?:d:)?href>")
                for (m in hrefRegex.findAll(body)) {
                    var href = m.groupValues[1].trim()
                    if (href.startsWith(listUrl)) href = href.removePrefix(listUrl)
                    href = href.trim('/')
                    if (href.startsWith("changes_") && href.endsWith(".json")) {
                        files.add(href)
                    }
                }
                if (files.isNotEmpty()) return files.sorted()
            }
        } catch (_: Exception) {}

        // 降级：GET 目录 + 正则
        try {
            val conn = URL(listUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("Authorization", basicAuth(config))
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val regex = Regex("changes_[a-z0-9]+_\\d+\\.json")
                for (m in regex.findAll(text)) files.add(m.value)
            }
        } catch (_: Exception) {}

        return files.sorted()
    }

    /** 从 WebDAV 下载文件内容 */
    private fun httpGetFile(config: WebDavConfig, file: String): String? {
        val url = config.url.trimEnd('/') + "/$SYNC_DIR/$file"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.setRequestProperty("Authorization", basicAuth(config))
        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else null
    }

    /** 从 WebDAV 删除已处理的文件 */
    private fun deleteRemoteFile(config: WebDavConfig, file: String) {
        try {
            val url = config.url.trimEnd('/') + "/$SYNC_DIR/$file"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("Authorization", basicAuth(config))
            conn.responseCode // 执行
        } catch (_: Exception) {}
    }

    // ── 工具方法 ──

    private fun basicAuth(config: WebDavConfig): String {
        val raw = "${config.username}:${config.password}"
        return "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** 从文件名提取 deviceId */
    private fun extractDeviceId(filename: String): String {
        // changes_XXXX_1234567890.json
        return filename.removePrefix("changes_").substringBeforeLast("_")
    }

    /** 从文件名提取时间戳 */
    private fun extractTimestamp(filename: String): Long? {
        return filename.removePrefix("changes_").substringAfterLast("_").removeSuffix(".json").toLongOrNull()
    }

    /** 统计变更总数 */
    private fun countChanges(changes: SyncChanges): Int {
        return changes.feedingRecords.size + changes.excreteRecords.size +
                changes.vaccinationRecords.size + changes.weightRecords.size +
                changes.babyProfile.size
    }
}
