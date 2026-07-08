// BabyCare/app/src/main/java/com/babycare/MainActivity.kt
package com.babycare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.babycare.data.SettingsManager
import com.babycare.databinding.ActivityMainBinding
import com.babycare.ui.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsManager
    private var requestStorageAttempted = false

    companion object {
        private const val REQUEST_STORAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsManager(this)
        // 应用保存的主题模式（在 setContentView 之前）
        applyThemeMode(settings.getThemeMode())

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(TimerFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_timer -> loadFragment(TimerFragment())
                R.id.nav_records -> loadFragment(RecordsFragment())
                R.id.nav_baby_growth -> loadFragment(BabyGrowthFragment())
                R.id.nav_settings -> loadFragment(SettingsFragment())
            }
            true
        }

        // 恢复底部导航选中状态（Activity 重建后 FragmentManager 自动恢复 Fragment）
        if (savedInstanceState != null) {
            restoreBottomNavSelection()
        }

        // 启动时申请存储权限
        requestStoragePermission()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
    }

    /**
     * 申请存储权限：
     * - Android 11+ (API 30+) : 使用 MANAGE_EXTERNAL_STORAGE，引导用户去系统设置
     * - Android 10 及以下 : 动态请求 WRITE_EXTERNAL_STORAGE
     */
    private fun requestStoragePermission() {
        if (requestStorageAttempted) return
        requestStorageAttempted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    Toast.makeText(this, "请在设置中允许「所有文件访问权限」以便备份数据", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    // 降级到应用详情页
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 动态请求存储权限
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(permission), REQUEST_STORAGE
                )
            } else {
                requestStorageAttempted = false // 已授权，下次不再请求
            }
        }
        // Android 5.1 及以下无需动态请求
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "存储权限被拒绝，部分功能可能受限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun applyThemeMode(mode: String) {
        val modeValue = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(modeValue)
    }

    /**
     * 根据 FragmentManager 当前显示的 Fragment 恢复底部导航高亮
     */
    private fun restoreBottomNavSelection() {
        val currentTag = supportFragmentManager.findFragmentById(R.id.fragment_container)?.let {
            when (it) {
                is com.babycare.ui.TimerFragment -> R.id.nav_timer
                is com.babycare.ui.RecordsFragment -> R.id.nav_records
                is com.babycare.ui.BabyGrowthFragment -> R.id.nav_baby_growth
                is com.babycare.ui.SettingsFragment -> R.id.nav_settings
                else -> null
            }
        }
        if (currentTag != null) {
            binding.bottomNav.selectedItemId = currentTag
        }
    }

    // applyBackground() 已移除 —— 背景图设置已被整体移除
}