// BabyCare/app/src/main/java/com/babycare/util/ExportUtil.kt
package com.babycare.util

import android.content.Context
import android.net.Uri
import com.babycare.data.ExcreteRecord
import com.babycare.data.FeedingRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object ExportUtil {
    fun exportToJson(context: Context, data: Any, filename: String): File? {
        return try {
            // 存到公开目录（更易访问）
            val dir = File(Constants.EXPORT_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            file.writeText(Gson().toJson(data))
            file
        } catch (e: Exception) {
            // 降级到app私有目录
            try {
                val dir = File(context.getExternalFilesDir(null), "logs")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                file.writeText(Gson().toJson(data))
                file
            } catch (e2: Exception) {
                null
            }
        }
    }

    /** 导入JSON */
    inline fun <reified T> importFromJson(context: Context, uri: Uri): List<T>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("无法打开文件")
            val json = inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<T>>() {}.type
            Gson().fromJson<List<T>>(json, type)
        } catch (e: Exception) {
            android.util.Log.w("ExportUtil", "导入JSON失败", e)
            null
        }
    }

    // ═══════════════════ 文本格式导出 ═══════════════════

    /** 导出喂养记录为可读文本 */
    fun exportFeedingRecordsText(context: Context, records: List<FeedingRecord>): File? {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        val sb = StringBuilder()
        sb.appendLine("========== 喂养记录导出 ==========")
        sb.appendLine("导出时间：${fmt.format(Instant.now())}")
        sb.appendLine("共 ${records.size} 条记录")
        sb.appendLine()

        records.forEachIndexed { i, r ->
            val timeStr = fmt.format(Instant.ofEpochMilli(r.timestamp))
            val typeLabel = when (r.feedType) {
                "breast" -> "🤱 母乳"
                "bottle_breast" -> "🍶 瓶喂母乳"
                else -> "🍼 配方奶"
            }
            val volumeStr = if (r.volume != null) " | ${r.volume} ml" else ""
            val diffStr = r.diff?.let {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                val hours = minutes / 60
                if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
            } ?: ""
            sb.appendLine("${i + 1}. $timeStr  $typeLabel$volumeStr")
            if (diffStr.isNotEmpty()) sb.appendLine("   $diffStr")
            sb.appendLine()
        }

        sb.appendLine("========== 导出结束 ==========")
        return writeTextFile(context, sb.toString(), "feeding_records_${System.currentTimeMillis()}.txt")
    }

    /** 导出排泄记录为可读文本 */
    fun exportExcreteRecordsText(context: Context, records: List<ExcreteRecord>): File? {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        val sb = StringBuilder()
        sb.appendLine("========== 排泄记录导出 ==========")
        sb.appendLine("导出时间：${fmt.format(Instant.now())}")
        sb.appendLine("共 ${records.size} 条记录")
        sb.appendLine()

        records.forEachIndexed { i, r ->
            val timeStr = fmt.format(Instant.ofEpochMilli(r.timestamp))
            val typeLabel = when (r.type) {
                "pee" -> "💧 小便"
                else -> {
                    val stateMap = mapOf("normal" to "🟤 正常", "loose" to "🟡 稀便", "hard" to "🟠 干硬")
                    "💩 大便 (${stateMap[r.state] ?: r.state})"
                }
            }
            val notePart = if (!r.note.isNullOrBlank()) " · 备注: ${r.note}" else ""
            val diffStr = r.diff?.let {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                val hours = minutes / 60
                if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
            } ?: ""
            sb.appendLine("${i + 1}. $timeStr  $typeLabel$notePart")
            if (diffStr.isNotEmpty()) sb.appendLine("   $diffStr")
            sb.appendLine()
        }

        sb.appendLine("========== 导出结束 ==========")
        return writeTextFile(context, sb.toString(), "excrete_records_${System.currentTimeMillis()}.txt")
    }

    /** 写文本文件 */
    private fun writeTextFile(context: Context, content: String, filename: String): File? {
        return try {
            val dir = File(Constants.EXPORT_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            file.writeText(content)
            file
        } catch (e: Exception) {
            try {
                val dir = File(context.getExternalFilesDir(null), "logs")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                file.writeText(content)
                file
            } catch (e2: Exception) {
                null
            }
        }
    }
}