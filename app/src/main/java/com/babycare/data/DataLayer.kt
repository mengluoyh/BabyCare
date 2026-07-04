package com.babycare.data

import android.content.Context
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
    val type: String,           // "auto" or "manual"
    val feedType: String = "formula", // "breast" or "formula" (old fragments)
    val volume: Int? = null,    // ml for formula (old fragments)
    val amount: Int = 0,        // ml (Compose)
    val duration: Int = 0,      // minutes (Compose)
    val diff: Long? = null      // ms since last feeding (old fragments)
)

@Entity(tableName = "excretion_records")
data class ExcretionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,    
    val note: String = ""
)

// ── DAOs ──────────────────────────────────────────────

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

// ── Database ──────────────────────────────────────────

@Database(
    entities = [BabyProfile::class, FeedingRecord::class, ExcretionRecord::class, ExcreteRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun babyDao(): BabyDao
    abstract fun feedingDao(): FeedingDao
    abstract fun excreteDao(): ExcreteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "babycare_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
