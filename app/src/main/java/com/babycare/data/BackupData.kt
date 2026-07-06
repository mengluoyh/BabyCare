// BabyCare/app/src/main/java/com/babycare/data/BackupData.kt
package com.babycare.data

/**
 * 统一备份数据模型，用于本地备份和 WebDAV 远程备份。
 */
data class BackupData(
    val feedingRecords: List<FeedingRecord> = emptyList(),
    val excreteRecords: List<ExcreteRecord> = emptyList(),
    val babyProfile: BabyProfile? = null,
    val weightRecords: List<WeightRecord> = emptyList(),
    val vaccinationRecords: List<VaccinationRecord> = emptyList(),
    val backupTime: Long = System.currentTimeMillis()
)
