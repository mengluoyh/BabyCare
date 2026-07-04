// BabyCare/app/src/main/java/com/babycare/MainActivity.kt
package com.babycare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.babycare.databinding.ActivityMainBinding
import com.babycare.ui.TimerFragment
import com.babycare.ui.FeedingRecordsFragment
import com.babycare.ui.ExcreteRecordsFragment
import com.babycare.ui.SettingsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(TimerFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_timer -> loadFragment(TimerFragment())
                R.id.nav_feeding -> loadFragment(FeedingRecordsFragment())
                R.id.nav_excrete -> loadFragment(ExcreteRecordsFragment())
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
}