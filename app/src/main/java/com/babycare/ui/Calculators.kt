package com.babycare.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object AgeCalculator {
    fun calculate(birthTimestamp: Long): Triple<Int, Int, Int> { // 月, 周, 天
        val birthDate = Instant.ofEpochMilli(birthTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        
        val totalDays = ChronoUnit.DAYS.between(birthDate, today).toInt()
        val months = (today.year - birthDate.year) * 12 + today.monthValue - birthDate.monthValue
        val remainDays = totalDays % 30 // 简化计算
        
        return Triple(months, totalDays / 7, totalDays)
    }
    
    fun getStartAndEndOfDay(): Pair<Long, Long> {
        val now = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        return Pair(now.toInstant().toEpochMilli(), now.plusDays(1).toInstant().toEpochMilli() - 1)
    }
}

object FeedingGuideline {
    // 根据天龄/周龄/月龄给出基础建议 (假设平均体重，用户可自定义覆盖)
    fun getSuggestedAmount(months: Int, weeks: Int, days: Int): Int {
        return when {
            days < 4 -> 400
            days < 30 -> 600
            months < 3 -> 800
            months < 6 -> 900
            else -> 600 // 6个月后添加辅食，奶量减少
        }
    }
}