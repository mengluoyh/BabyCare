// BabyCare/app/src/main/java/com/example/babycare/data/ExcreteRecord.kt
package com.babycare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "excrete_records")
data class ExcreteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,       // "bowel" or "pee"
    val state: String?,     // for bowel: "normal","loose","hard"
    val note: String?,
    val timestamp: Long,
    val diff: Long?
)