// BabyCare/app/src/main/java/com/example/babycare/BabyCareApp.kt
package com.babycare

import android.app.Application
import com.babycare.data.AppDatabase

class BabyCareApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
}