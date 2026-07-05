// BabyCare/app/src/main/java/com/babycare/util/WebDavUtil.kt
package com.babycare.util

import android.content.Context
import com.babycare.BabyCareApp
import com.babycare.data.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object WebDavUtil {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json".toMediaType()

    data class BackupData(
        val feedingRecords: List<com.babycare.data.FeedingRecord> = emptyList(),
        val excreteRecords: List<com.babycare.data.ExcreteRecord> = emptyList(),
        val babyProfile: com.babycare.data.BabyProfile? = null,
        val backupTime: Long = System.currentTimeMillis()
    )

    fun backup(context: Context, onResult: (Boolean, String) -> Unit) {
        val settings = SettingsManager(context)
        val url = settings.getWebDavUrl()
        val user = settings.getWebDavUser()
        val pass = settings.getWebDavPass()

        if (url.isBlank()) {
            onResult(false, "WebDAV地址未设置")
            return
        }

        val app = context.applicationContext as BabyCareApp
        Thread {
            try {
                // 使用 runBlocking 在后台线程中执行 Room suspend 查询
                val (feedings, excretes, profile) = runBlocking(Dispatchers.IO) {
                    val db = app.database
                    val f = db.feedingDao().getFeedingsBetween(0, Long.MAX_VALUE)
                    val e = db.excreteDao().getExcretesBetween(0, Long.MAX_VALUE)
                    val p = db.babyDao().getProfileSync()
                    Triple(f, e, p)
                }

                val backupData = BackupData(
                    feedingRecords = feedings,
                    excreteRecords = excretes,
                    babyProfile = profile
                )

                val json = Gson().toJson(backupData)
                val filename = "babycare_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"

                // WebDAV PUT
                val requestUrl = "${url.trimEnd('/')}/$filename"
                val credential = Credentials.basic(user, pass)
                val body = json.toRequestBody(JSON_MEDIA)

                val request = Request.Builder()
                    .url(requestUrl)
                    .put(body)
                    .header("Authorization", credential)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        settings.saveLastWebDavSyncTime(System.currentTimeMillis())
                        onResult(true, "备份成功：$filename")
                    } else {
                        onResult(false, "备份失败：${response.code} ${response.message}")
                    }
                }
            } catch (e: Exception) {
                onResult(false, "备份出错：${e.message}")
            }
        }.start()
    }

    fun restore(context: Context, jsonData: String, onResult: (Boolean, String) -> Unit) {
        val app = context.applicationContext as BabyCareApp
        Thread {
            try {
                val backupData = Gson().fromJson(jsonData, BackupData::class.java)
                runBlocking(Dispatchers.IO) {
                    val db = app.database
                    db.feedingDao().insertAll(backupData.feedingRecords)
                    db.excreteDao().insertAll(backupData.excreteRecords)
                    backupData.babyProfile?.let { db.babyDao().upsertProfile(it) }
                }
                onResult(true, "恢复成功：${backupData.feedingRecords.size}条喂养记录，${backupData.excreteRecords.size}条排泄记录")
            } catch (e: Exception) {
                onResult(false, "恢复失败：${e.message}")
            }
        }.start()
    }
}