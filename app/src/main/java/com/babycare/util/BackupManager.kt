// BabyCare/app/src/main/java/com/babycare/util/BackupManager.kt
package com.babycare.util

import android.content.Context
import com.babycare.BabyCareApp
import com.babycare.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 统一备份管理器：仅本地备份，全协程实现
 * 备份格式为JSON，兼容旧版备份文件的恢复
 */
object BackupManager {

    /** 执行完整备份（仅本地） */
    suspend fun backupAll(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val data = fetchAllData(context)
            val file = localBackup(data)
            Result.success("本地备份成功:${file.name}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 列出所有本地备份文件 */
    fun listBackupFiles(): List<File> {
        val dir = File(Constants.BACKUP_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.name.startsWith(Constants.BACKUP_PREFIX) && it.name.endsWith(Constants.BACKUP_SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** 从JSON文件恢复数据 */
    suspend fun restoreFromFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonData = file.readText()
            val data = Gson().fromJson(jsonData, BackupData::class.java)
            val app = BabyCareApp.instance
            val db = app.database
            if (data.feedingRecords.isNotEmpty()) db.feedingDao().insertAll(data.feedingRecords)
            if (data.excreteRecords.isNotEmpty()) db.excreteDao().insertAll(data.excreteRecords)
            data.babyProfile?.let { db.babyDao().upsertProfile(it) }
            if (data.weightRecords.isNotEmpty()) data.weightRecords.forEach { db.weightDao().insert(it) }
            if (data.vaccinationRecords.isNotEmpty()) db.vaccineDao().insertAll(data.vaccinationRecords)
            Result.success("恢复成功:${data.feedingRecords.size}条喂养,${data.excreteRecords.size}条排泄,${data.weightRecords.size}条体重,${data.vaccinationRecords.size}条疫苗")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════ 内部方法 ═══════════════════

    private suspend fun fetchAllData(context: Context): BackupData {
        val db = (context.applicationContext as BabyCareApp).database
        return BackupData(
            feedingRecords = db.feedingDao().getAllSnapshot(),
            excreteRecords = db.excreteDao().getAllSnapshot(),
            babyProfile = db.babyDao().getProfileSync(),
            weightRecords = db.weightDao().getAllSnapshot(),
            vaccinationRecords = db.vaccineDao().getAllSnapshot(),
            backupTime = System.currentTimeMillis()
        )
    }

    private fun localBackup(data: BackupData): File {
        val dir = File(Constants.BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()

        val json = Gson().toJson(data)
        val filename = "${Constants.BACKUP_PREFIX}${DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())}${Constants.BACKUP_SUFFIX}"
        val file = File(dir, filename)
        file.writeText(json)
        return file
    }
}