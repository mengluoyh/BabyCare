// BabyCare/app/src/main/java/com/example/babycare/util/ExportUtil.kt
package com.babycare.util

import android.content.Context
import com.google.gson.Gson
import java.io.File

object ExportUtil {
    fun exportToJson(context: Context, data: Any, filename: String): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            file.writeText(Gson().toJson(data))
            file
        } catch (e: Exception) {
            null
        }
    }
}