// BabyCare/app/src/main/java/com/babycare/MainActivity.kt
package com.babycare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.babycare.data.SettingsManager
import com.babycare.databinding.ActivityMainBinding
import com.babycare.ui.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsManager

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
}