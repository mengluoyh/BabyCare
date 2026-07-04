package com.babycare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.babycare.ui.screens.HomeScreen // ✅ 确保这里是 com.babycare
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        
        // 🛡️ 全局异常捕获器（防闪退日志记录）
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val errorLog = sw.toString()

                val logDir = getExternalFilesDir(null)
                if (logDir != null) {
                    val logFile = File(logDir, "CrashLog.txt")
                    logFile.writeText("崩溃时间: ${Date()}\n\n错误原因:\n$errorLog")
                }

                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(1)
            } catch (e: Exception) { }
        }

        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 直接调用 HomeScreen，ViewModel 会在 HomeScreen 内部自动初始化
                    HomeScreen() 
                }
            }
        }
    }
}