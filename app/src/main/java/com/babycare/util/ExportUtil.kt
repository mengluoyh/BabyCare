// BabyCare/app/src/main/java/com/babycare/util/ExportUtil.kt
package com.babycare.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException

object ExportUtil {
    fun exportToJson(context: Context, data: Any, filename: String): File? {
        return try {
            // 存到 /storage/emulated/0/BabyCare/（更易访问）
            val dir = File("/storage/emulated/0/BabyCare")
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

    /** 从JSON文件中导入数据 */
    inline fun <reified T> importFromJson(context: Context, uri: android.net.Uri): List<T>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("无法打开文件")
            val json = inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<T>>() {}.type
            Gson().fromJson<List<T>>(json, type)
        } catch (e: Exception) {
            null
        }
    }
}