// BabyCare/app/src/main/java/com/babycare/util/IconChanger.kt
package com.babycare.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconChanger {
    const val ICON_ORANGE = "orange"
    const val ICON_BLUE = "blue"
    const val ICON_PINK = "pink"
    const val ICON_GREEN = "green"
    const val ICON_PURPLE = "purple"
    const val ICON_TEAL = "teal"

    private val iconAliases = mapOf(
        ICON_ORANGE to ".MainActivity_Orange",
        ICON_BLUE to ".MainActivity_Blue",
        ICON_PINK to ".MainActivity_Pink",
        ICON_GREEN to ".MainActivity_Green",
        ICON_PURPLE to ".MainActivity_Purple",
        ICON_TEAL to ".MainActivity_Teal"
    )

    fun setIcon(context: Context, iconName: String) {
        val pm = context.packageManager
        val targetAlias = iconAliases[iconName] ?: iconAliases[ICON_ORANGE]!!
        for ((_, alias) in iconAliases) {
            pm.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}$alias"),
                if (alias == targetAlias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    fun getCurrentIcon(context: Context): String {
        val pm = context.packageManager
        for ((name, alias) in iconAliases) {
            val state = pm.getComponentEnabledSetting(
                ComponentName(context, "${context.packageName}$alias")
            )
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return name
        }
        return ICON_ORANGE
    }

    fun getIconLabel(iconName: String): String = when (iconName) {
        ICON_ORANGE -> "🟠 橙色"
        ICON_BLUE -> "🔵 蓝色"
        ICON_PINK -> "🩷 粉色"
        ICON_GREEN -> "🟢 绿色"
        ICON_PURPLE -> "🟣 紫色"
        ICON_TEAL -> "🔷 青色"
        else -> "🟠 橙色"
    }
}