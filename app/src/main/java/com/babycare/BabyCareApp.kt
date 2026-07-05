// BabyCare/app/src/main/java/com/babycare/BabyCareApp.kt
package com.babycare

import android.app.Application
import com.babycare.data.AppDatabase

class BabyCareApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }

    companion object {
        lateinit var instance: BabyCareApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}