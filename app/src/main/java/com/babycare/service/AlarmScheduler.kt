// BabyCare/app/src/main/java/com/example/babycare/service/AlarmScheduler.kt
package com.babycare.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class AlarmScheduler(private val context: Context) {
    companion object {
        const val NOTIFICATION_ID = 1001
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(timeInMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android12+ 需要 SCHEDULE_EXACT_ALARM 权限才能设置精确闹钟
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending)
            } else {
                // 无权限则用 inexact，不会崩溃但可能有延迟
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeInMillis, pending)
                Toast.makeText(
                    context,
                    "⚠️ 未获精确闹钟权限，倒计时可能延迟。请在系统设置中允许「精确的闹钟」",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pending)
        }
    }

    fun cancelAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }
}