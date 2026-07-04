package com.babycare.util

import java.util.Calendar

object AgeCalculator {
    /** 计算年龄：返回 (月, 周, 剩余天数) */
    fun calculateAge(birthDate: Long): Triple<Int, Int, Int> {
        val birth = Calendar.getInstance().apply { timeInMillis = birthDate }
        val now = Calendar.getInstance()
        var months = (now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)) * 12 +
                (now.get(Calendar.MONTH) - birth.get(Calendar.MONTH))
        var days = now.get(Calendar.DAY_OF_MONTH) - birth.get(Calendar.DAY_OF_MONTH)
        if (days < 0) {
            months--
            val prev = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prev.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        val weeks = days / 7
        val remainDays = days % 7
        return Triple(months, weeks, remainDays)
    }

    /** 计算总天数（从出生当天算第1天） */
    fun totalDays(birthDate: Long): Int {
        val birth = Calendar.getInstance().apply { timeInMillis = birthDate }
        val now = Calendar.getInstance()
        // 将时间归零到当天00:00，精确计算天数差
        birth.set(Calendar.HOUR_OF_DAY, 0)
        birth.set(Calendar.MINUTE, 0)
        birth.set(Calendar.SECOND, 0)
        birth.set(Calendar.MILLISECOND, 0)
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val diff = now.timeInMillis - birth.timeInMillis
        val days = (diff / (24 * 60 * 60 * 1000L)).toInt()
        return days + 1 // 含当天（出生日算第1天）
    }

    /** 获取当日 00:00 到 23:59:59 的时间戳范围 */
    fun getTodayRange(): Pair<Long, Long> {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val start = now.timeInMillis
        now.add(Calendar.DAY_OF_MONTH, 1)
        val end = now.timeInMillis - 1
        return Pair(start, end)
    }

    /** 获取过去n天的开始时间戳 */
    fun getPastDaysStart(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -days)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 根据年龄（月）返回默认建议配方奶量 */
    fun getSuggestedFormula(months: Int): Int = when {
        months < 1 -> 700
        months < 3 -> 800
        months < 6 -> 900
        else -> 1000
    }
}