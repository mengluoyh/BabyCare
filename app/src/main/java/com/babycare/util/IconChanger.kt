// BabyCare/app/src/main/java/com/babycare/util/IconChanger.kt
package com.babycare.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 运行时动态切换APP启动图标（通过 ActivityAlias）
 *
 * 使用方式：
 *   IconChanger.setIcon(context, IconChanger.ICON_BLUE) // 切换为蓝色
 *   IconChanger.setIcon(context, IconChanger.ICON_PINK) // 切换为粉色
 *   IconChanger.setIcon(context, IconChanger.ICON_ORANGE) // 切换回橙色（默认）
 */
object IconChanger {

    const val ICON_ORANGE = "orange"
    const val ICON_BLUE = "blue"
    const val ICON_PINK = "pink"

    private val iconAliases = mapOf(
        ICON_ORANGE to ".MainActivity_Orange",
        ICON_BLUE to ".MainActivity_Blue",
        ICON_PINK to ".MainActivity_Pink"
    )

    /**
     * 切换到指定图标
     * @param iconName ICON_ORANGE / ICON_BLUE / ICON_PINK
     */
    fun setIcon(context: Context, iconName: String) {
        val pm = context.packageManager
        val targetAlias = iconAliases[iconName] ?: iconAliases[ICON_ORANGE]!!

        // 先禁用所有图标别名
        for ((_, alias) in iconAliases) {
            pm.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}$alias"),
                if (alias == targetAlias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /**
     * 获取当前启用的图标名称
     */
    fun getCurrentIcon(context: Context): String {
        val pm = context.packageManager
        for ((name, alias) in iconAliases) {
            val state = pm.getComponentEnabledSetting(
                ComponentName(context, "${context.packageName}$alias")
            )
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return name
            }
        }
        return ICON_ORANGE
    }
}