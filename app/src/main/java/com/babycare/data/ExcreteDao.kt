// BabyCare/app/src/main/java/com/example/babycare/data/ExcreteDao.kt
package com.babycare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcreteDao {
    @Query("SELECT * FROM excrete_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ExcreteRecord>>

    @Query("SELECT * FROM excrete_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): ExcreteRecord?

    @Insert
    suspend fun insert(record: ExcreteRecord)

    @Delete
    suspend fun delete(record: ExcreteRecord)

    @Query("DELETE FROM excrete_records")
    suspend fun deleteAll()
}