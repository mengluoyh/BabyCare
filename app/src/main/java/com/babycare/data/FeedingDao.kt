// BabyCare/app/src/main/java/com/example/babycare/data/FeedingDao.kt
package com.babycare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingDao {
    @Query("SELECT * FROM feeding_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FeedingRecord>>

    @Query("SELECT * FROM feeding_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): FeedingRecord?

    @Insert
    suspend fun insert(record: FeedingRecord)

    @Delete
    suspend fun delete(record: FeedingRecord)

    @Query("DELETE FROM feeding_records")
    suspend fun deleteAll()
}