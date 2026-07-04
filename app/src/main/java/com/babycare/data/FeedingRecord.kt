// BabyCare/app/src/main/java/com/example/babycare/data/FeedingRecord.kt
package com.babycare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeding_records")
data class FeedingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,       // "auto" or "manual"
    val feedType: String,   // "breast" or "formula"
    val volume: Int?,
    val timestamp: Long,
    val diff: Long?         // milliseconds since last feeding
)