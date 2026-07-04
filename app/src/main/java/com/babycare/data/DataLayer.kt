package com.babycare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "baby_profile")
data class BabyProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "宝宝",
    val birthDate: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val customFormulaTarget: Int = 800 
)

@Entity(tableName = "feeding_records")
data class FeedingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, 
    val type: String,    
    val amount: Int = 0, 
    val duration: Int = 0 
)

@Entity(tableName = "excretion_records")
data class ExcretionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,    
    val note: String = ""
)

@Dao
interface BabyDao {
    @Query("SELECT * FROM baby_profile WHERE id = 1")
    fun getProfile(): Flow<BabyProfile?>

    @Upsert
    suspend fun upsertProfile(profile: BabyProfile)

    @Query("SELECT * FROM feeding_records WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getFeedings(start: Long, end: Long): Flow<List<FeedingRecord>>

    @Query("SELECT * FROM excretion_records WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getExcretions(start: Long, end: Long): Flow<List<ExcretionRecord>>

    @Insert
    suspend fun insertFeeding(record: FeedingRecord)

    @Insert
    suspend fun insertExcretion(record: ExcretionRecord)
}

@Database(entities = [BabyProfile::class, FeedingRecord::class, ExcretionRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun babyDao(): BabyDao
}
