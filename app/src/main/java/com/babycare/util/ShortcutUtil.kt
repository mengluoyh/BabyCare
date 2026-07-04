package com.babycare.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.babycare.MainActivity
import com.babycare.R
import java.io.File

object ShortcutUtil {
    fun createCustomShortcut(context: Context, iconPath: String?, babyName: String) {
        val intent = Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_VIEW }

        val icon = if (iconPath != null && File(iconPath).exists()) {
            IconCompat.createWithContentUri("file://$iconPath")  // 修复
        } else {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "custom_$babyName")
            .setShortLabel(babyName)
            .setIcon(icon)
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}