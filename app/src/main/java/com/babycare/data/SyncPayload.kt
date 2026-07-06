// BabyCare/app/src/main/java/com/babycare/data/SyncPayload.kt
package com.babycare.data

/**
 * WebDAV 增量同步数据模型
 * 每台设备上传自己的增量变更，下载其他设备的变更进行合并。
 */
data class SyncPayload(
    val deviceId: String,
    val syncTime: Long,
    val changes: SyncChanges
)

data class SyncChanges(
    val feedingRecords: List<FeedingRecord> = emptyList(),
    val excreteRecords: List<ExcreteRecord> = emptyList(),
    val vaccinationRecords: List<VaccinationRecord> = emptyList(),
    val weightRecords: List<WeightRecord> = emptyList(),
    val babyProfile: List<BabyProfile> = emptyList()
)

data class SyncSummary(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val errors: List<String> = emptyList()
)
