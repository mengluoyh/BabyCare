// BabyCare/app/src/main/java/com/babycare/util/BackupManager.kt
package com.babycare.util

import android.content.Context
import com.babycare.BabyCareApp
import com.babycare.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 统一备份管理器：仅本地备份
 * 备份格式为JSON，兼容旧版备份文件的恢复
 */
object BackupManager {

    data class BackupData(
        val feedingRecords: List<FeedingRecord> = emptyList(),
        val excreteRecords: List<ExcreteRecord> = emptyList(),
        val babyProfile: BabyProfile? = null,
        val backupTime: Long = System.currentTimeMillis()
    )

    /** 执行完整备份（仅本地），通过回调报告结果 */
    fun backupAll(context: Context, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val data = fetchAllData(context)
                val file = localBackup(context, data)
                onResult(true, "本地备份成功:${file.name}")
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

    /** 从JSON恢复数据 */
    fun restore(context: Context, jsonData: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val data = Gson().fromJson(jsonData, BackupData::class.java)
                runBlocking(Dispatchers.IO) {
                    val db = (context.applicationContext as BabyCareApp).database
                    if (data.feedingRecords.isNotEmpty()) db.feedingDao().insertAll(data.feedingRecords)
                    if (data.excreteRecords.isNotEmpty()) db.excreteDao().insertAll(data.excreteRecords)
                    data.babyProfile?.let { db.babyDao().upsertProfile(it) }
                }
                onResult(true, "恢复成功:${data.feedingRecords.size}条喂养,${data.excreteRecords.size}条排泄")
            } catch (e: Exception) {
                onResult(false, "恢复失败:${e.message}")
            }
        }.start()
    }
}