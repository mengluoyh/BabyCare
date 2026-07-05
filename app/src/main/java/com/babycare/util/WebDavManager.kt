// BabyCare/app/src/main/java/com/babycare/util/WebDavManager.kt
package com.babycare.util

import android.content.Context
import android.util.Base64
import com.babycare.BabyCareApp
import com.babycare.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * WebDAV 备份管理：通过 HTTP PUT/GET 将备份文件同步到远程 WebDAV 服务器。
 */
object WebDavManager {

    data class BackupData(
        val feedingRecords: List<FeedingRecord> = emptyList(),
        val excreteRecords: List<ExcreteRecord> = emptyList(),
        val babyProfile: BabyProfile? = null,
        val weightRecords: List<WeightRecord> = emptyList(),
        val vaccinationRecords: List<VaccinationRecord> = emptyList(),
        val backupTime: Long = System.currentTimeMillis()
    )

    private const val TIMEOUT = 15000
    private const val INDEX_FILE = "babycare_index.json"

    /** 上传备份到 WebDAV */
    suspend fun upload(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig(context) ?: return@withContext Result.failure(Exception("未配置 WebDAV"))
            if (config.url.isBlank()) return@withContext Result.failure(Exception("WebDAV 地址为空"))

            val data = fetchAllData()
            val json = Gson().toJson(data)
            val filename = "babycare_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val baseUrl = config.url.trimEnd('/')
            val targetUrl = baseUrl + "/" + filename

            // 上传备份文件
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
                return@withContext Result.failure(Exception("WebDAV 上传失败: HTTP $code"))
            }

            saveUploadedFilename(context, filename)

            // 更新远程索引文件（方便跨设备恢复）
            updateRemoteIndex(config, baseUrl, filename)

            Result.success("WebDAV 上传成功: $filename")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 更新远程索引文件：追加新文件名 */
    private fun updateRemoteIndex(config: WebDavConfig, baseUrl: String, newFilename: String) {
        val indexUrl = baseUrl + "/" + INDEX_FILE
        // 读取已有索引
        val existing = mutableListOf<String>()
        try {
            val getConn = URL(indexUrl).openConnection() as HttpURLConnection
            getConn.requestMethod = "GET"
            getConn.connectTimeout = TIMEOUT
            getConn.readTimeout = TIMEOUT
            getConn.setRequestProperty("Authorization", basicAuth(config))
            if (getConn.responseCode in 200..299) {
                val text = getConn.inputStream.bufferedReader().use { it.readText() }
                val arr = Gson().fromJson(text, Array<String>::class.java)
                if (arr != null) existing.addAll(arr)
            }
        } catch (_: Exception) {}

        // 去重 + 加入新文件名
        val all = (existing + newFilename).distinct()
        val updatedJson = Gson().toJson(all)

        // PUT 更新索引
        try {
            val putConn = URL(indexUrl).openConnection() as HttpURLConnection
            putConn.requestMethod = "PUT"
            putConn.doOutput = true
            putConn.connectTimeout = TIMEOUT
            putConn.readTimeout = TIMEOUT
            putConn.setRequestProperty("Authorization", basicAuth(config))
            putConn.setRequestProperty("Content-Type", "application/json")
            putConn.outputStream.use { it.write(updatedJson.toByteArray(Charsets.UTF_8)) }
            val putCode = putConn.responseCode
            if (putCode !in 200..299) {
                android.util.Log.w("WebDavManager", "索引文件更新失败: HTTP $putCode")
            }
        } catch (e: Exception) {
            android.util.Log.w("WebDavManager", "索引文件更新异常: ${e.message}")
        }
    }

    /** 从 WebDAV 下载并恢复最新备份 */
    suspend fun downloadAndRestore(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig(context) ?: return@withContext Result.failure(Exception("未配置 WebDAV"))

            // 获取备份文件列表
            val baseUrl = config.url.trimEnd('/')
            val files = listBackupFiles(config, context)

            if (files.isEmpty()) {
                return@withContext Result.failure(Exception("远程没有备份文件"))
            }

            // 下载最新备份
            val downloadUrl = baseUrl + "/" + files.first()
            val getConn = URL(downloadUrl).openConnection() as HttpURLConnection
            getConn.requestMethod = "GET"
            getConn.connectTimeout = TIMEOUT
            getConn.readTimeout = TIMEOUT
            getConn.setRequestProperty("Authorization", basicAuth(config))

            val getCode = getConn.responseCode
            if (getCode !in 200..299) {
                return@withContext Result.failure(Exception("WebDAV 下载失败: HTTP $getCode"))
            }

            val json = getConn.inputStream.bufferedReader().use { it.readText() }
            val data = Gson().fromJson(json, BackupData::class.java)

            // 恢复到数据库
            val app = BabyCareApp.instance
            val db = app.database
            if (data.feedingRecords.isNotEmpty()) db.feedingDao().insertAll(data.feedingRecords)
            if (data.excreteRecords.isNotEmpty()) db.excreteDao().insertAll(data.excreteRecords)
            data.babyProfile?.let { db.babyDao().upsertProfile(it) }
            if (data.weightRecords.isNotEmpty()) data.weightRecords.forEach { db.weightDao().insert(it) }
            if (data.vaccinationRecords.isNotEmpty()) db.vaccineDao().insertAll(data.vaccinationRecords)

            Result.success("WebDAV 恢复成功: ${data.feedingRecords.size}条喂养, ${data.excreteRecords.size}条排泄, ${data.weightRecords.size}条体重, ${data.vaccinationRecords.size}条疫苗")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 列出远程备份文件 */
    suspend fun listRemoteFiles(context: Context): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig(context) ?: return@withContext Result.failure(Exception("未配置 WebDAV"))
            val files = listBackupFiles(config, context)
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 保存 WebDAV 配置 */
    fun saveConfig(context: Context, url: String, username: String, password: String) {
        context.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE).edit()
            .putString("webdav_url", url)
            .putString("webdav_username", username)
            .putString("webdav_password", password)
            .apply()
    }

    /** 加载 WebDAV 配置 */
    fun loadConfig(context: Context): WebDavConfig? {
        val prefs = context.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("webdav_url", null) ?: return null
        val username = prefs.getString("webdav_username", "") ?: ""
        val password = prefs.getString("webdav_password", "") ?: ""
        return WebDavConfig(url, username, password)
    }

    /** 保存已上传的文件名到本地，用于列表接口不可用时的恢复 */
    private fun saveUploadedFilename(context: Context, filename: String) {
        val prefs = context.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("uploaded_files", mutableSetOf()) ?: mutableSetOf()
        val newSet = set.toMutableSet()
        newSet.add(filename)
        prefs.edit().putStringSet("uploaded_files", newSet).apply()
    }

    /** 读取本地记录的已上传文件名列表 */
    private fun getUploadedFilenames(context: Context): List<String> {
        val prefs = context.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE)
        return (prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()).sortedDescending()
    }

    data class WebDavConfig(
        val url: String,
        val username: String,
        val password: String
    )

    // ═══════════════════ 内部方法 ═══════════════════

    private fun basicAuth(config: WebDavConfig): String {
        val raw = "${config.username}:${config.password}"
        return "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private suspend fun fetchAllData(): BackupData {
        val app = BabyCareApp.instance
        val db = app.database
        return BackupData(
            feedingRecords = db.feedingDao().getAllSnapshot(),
            excreteRecords = db.excreteDao().getAllSnapshot(),
            babyProfile = db.babyDao().getProfileSync(),
            weightRecords = db.weightDao().getAllSnapshot(),
            vaccinationRecords = db.vaccineDao().getAllSnapshot(),
            backupTime = System.currentTimeMillis()
        )
    }

    /** 列出 WebDAV 上所有备份文件（按文件名倒序） */
    private fun listBackupFiles(config: WebDavConfig, context: Context? = null): List<String> {
        val listUrl = config.url.trimEnd('/') + "/"

        // 首选 PROPFIND（标准 WebDAV），失败则降级
        val body: String? = try {
            val conn = URL(listUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "PROPFIND"
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("Authorization", basicAuth(config))
            conn.setRequestProperty("Depth", "1")
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        val files = mutableListOf<String>()

        if (body != null) {
            // 解析 PROPFIND XML
            val responseRegex = Regex("<(?:d:)?response>(.*?)</(?:d:)?response>", RegexOption.DOT_MATCHES_ALL)
            val hrefRegex = Regex("<(?:d:)?href>(.*?)</(?:d:)?href>")
            for (match in responseRegex.findAll(body)) {
                val hrefMatch = hrefRegex.find(match.groupValues[1])
                if (hrefMatch != null) {
                    var href = hrefMatch.groupValues[1].trim()
                    if (href.startsWith(listUrl)) href = href.removePrefix(listUrl)
                    href = href.trim('/')
                    if (href.startsWith("babycare_backup_") && href.endsWith(".json")) {
                        files.add(href)
                    }
                }
            }
            if (files.isNotEmpty()) return files.sortedByDescending { it }
        }

        // 降级：GET 请求目录，从 HTML/文本中正则提取文件名
        try {
            val conn = URL(listUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("Authorization", basicAuth(config))
            val code = conn.responseCode
            if (code in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val hrefRegex = Regex("""href=["']([^"']*babycare_backup_\d{8}_\d{6}\.json)["']""", RegexOption.IGNORE_CASE)
                for (m in hrefRegex.findAll(text)) files.add(m.groupValues[1].trim())
                if (files.isEmpty()) {
                    val rawRegex = Regex("babycare_backup_\\d{8}_\\d{6}\\.json")
                    for (m in rawRegex.findAll(text)) files.add(m.value)
                }
            }
            if (files.isNotEmpty()) return files.sortedByDescending { it }
        } catch (_: Exception) {
            // GET 也失败，继续降级
        }

        // 降级：尝试读取远程索引文件 babycare_index.json
        try {
            val indexUrl = listUrl + INDEX_FILE
            val idxConn = URL(indexUrl).openConnection() as HttpURLConnection
            idxConn.requestMethod = "GET"
            idxConn.connectTimeout = TIMEOUT
            idxConn.readTimeout = TIMEOUT
            idxConn.setRequestProperty("Authorization", basicAuth(config))
            if (idxConn.responseCode in 200..299) {
                val text = idxConn.inputStream.bufferedReader().use { it.readText() }
                val arr = Gson().fromJson(text, Array<String>::class.java)
                if (arr != null) files.addAll(arr.filter { it.startsWith("babycare_backup_") && it.endsWith(".json") })
            }
            if (files.isNotEmpty()) return files.sortedByDescending { it }
        } catch (_: Exception) {}

        // 最终降级：使用本地记录的上传文件名，逐个尝试 GET 验证
        if (context != null) {
            for (name in getUploadedFilenames(context)) {
                try {
                    val testUrl = listUrl + name
                    val testConn = URL(testUrl).openConnection() as HttpURLConnection
                    testConn.requestMethod = "GET"
                    testConn.connectTimeout = TIMEOUT
                    testConn.readTimeout = TIMEOUT
                    testConn.setRequestProperty("Authorization", basicAuth(config))
                    if (testConn.responseCode in 200..299) {
                        files.add(name)
                    }
                } catch (_: Exception) {}
            }
        }

        return files.sortedByDescending { it }
    }
}