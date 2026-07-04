package com.babycare.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

object AgeCalculator {
    /** 计算年龄：返回 (月, 周, 天) */
    fun calculateAge(birthDate: Long): Triple<Int, Int, Int> {
        val birth = Calendar.getInstance().apply { timeInMillis = birthDate }
        val now = Calendar.getInstance()
        var months = (now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)) * 12 + (now.get(Calendar.MONTH) - birth.get(Calendar.MONTH))
        var days = now.get(Calendar.DAY_OF_MONTH) - birth.get(Calendar.DAY_OF_MONTH)
        if (days < 0) {
            months--
            val prev = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prev.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        val weeks = days / 7
        days %= 7
        return Triple(months, weeks, days)
    }

    /** 获取当日 00:00 到 23:59:59 的时间戳范围 */
    fun getStartAndEndOfDay(): Pair<Long, Long> {
        val now = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        return Pair(now.toInstant().toEpochMilli(), now.plusDays(1).toInstant().toEpochMilli() - 1)
    }

    fun getDailySuggestionMonths(months: Int): Int = when {
        months < 1 -> 700
        months < 3 -> 800
        months < 6 -> 900
        else -> 1000
    }
}