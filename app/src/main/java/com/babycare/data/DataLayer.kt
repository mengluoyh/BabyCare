package com.babycare.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ─── 实体 ───────────────────────────────────────────────

@Entity(tableName = "baby_profile")
data class BabyProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "宝宝",
    val birthDate: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val customFormulaTarget: Int = 800,
    val formulaAgeUnit: String = "month" // "day" | "week" | "month"
)

@Entity(tableName = "feeding_records")
data class FeedingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,         // "auto" or "manual"
    val feedType: String,     // "breast" or "formula"
    val volume: Int? = null,  // ml for formula
    val diff: Long? = null    // ms since last feeding
)

@Entity(tableName = "excrete_records")
data class ExcreteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,          // "bowel" or "pee"
    val state: String? = null, // "normal" | "loose" | "hard"
    val note: String? = null,
    val diff: Long? = null
)

// ─── DAO ────────────────────────────────────────────────

@Dao
interface FeedingDao {
    @Query("SELECT * FROM feeding_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FeedingRecord>>

    @Query("SELECT * FROM feeding_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): FeedingRecord?

    @Query("SELECT * FROM feeding_records WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp")
    suspend fun getFeedingsBetween(start: Long, end: Long): List<FeedingRecord>

    @Query("SELECT * FROM feeding_records WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp")
    fun getFeedingsBetweenFlow(start: Long, end: Long): Flow<List<FeedingRecord>>

    @Query("SELECT COUNT(*) FROM feeding_records WHERE feedType = 'breast' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getBreastCountBetween(start: Long, end: Long): Int

    @Query("SELECT COALESCE(SUM(volume), 0) FROM feeding_records WHERE feedType = 'formula' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getFormulaTotalBetween(start: Long, end: Long): Int

    @Query("SELECT COALESCE(SUM(volume), 0) FROM feeding_records WHERE feedType = 'formula' AND timestamp >= :start AND timestamp <= :end GROUP BY strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') ORDER BY timestamp DESC LIMIT 7")
    suspend fun getDailyFormulaLast7Days(start: Long, end: Long): List<Int>

    @Query("SELECT COUNT(*) FROM feeding_records WHERE feedType = 'breast' AND timestamp >= :start AND timestamp <= :end GROUP BY strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') ORDER BY timestamp DESC LIMIT 7")
    suspend fun getDailyBreastCountLast7Days(start: Long, end: Long): List<Int>

    @Insert
    suspend fun insert(record: FeedingRecord)

    @Insert
    suspend fun insertAll(records: List<FeedingRecord>)

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

    @Query("SELECT * FROM excrete_records WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp")
    suspend fun getExcretesBetween(start: Long, end: Long): List<ExcreteRecord>

    @Query("SELECT COUNT(*) FROM excrete_records WHERE type = 'bowel' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getBowelCountBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM excrete_records WHERE type = 'pee' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getPeeCountBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM excrete_records WHERE type = 'bowel' AND timestamp >= :start AND timestamp <= :end GROUP BY strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') ORDER BY timestamp ASC")
    suspend fun getDailyBowelCountsBetween(start: Long, end: Long): List<Int>

    @Query("SELECT COUNT(*) FROM excrete_records WHERE type = 'pee' AND timestamp >= :start AND timestamp <= :end GROUP BY strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') ORDER BY timestamp ASC")
    suspend fun getDailyPeeCountsBetween(start: Long, end: Long): List<Int>

    @Insert
    suspend fun insert(record: ExcreteRecord)

    @Insert
    suspend fun insertAll(records: List<ExcreteRecord>)

    @Delete
    suspend fun delete(record: ExcreteRecord)

    @Query("DELETE FROM excrete_records")
    suspend fun deleteAll()
}

@Dao
interface BabyDao {
    @Query("SELECT * FROM baby_profile WHERE id = 1")
    fun getProfile(): Flow<BabyProfile?>

    @Query("SELECT * FROM baby_profile WHERE id = 1")
    suspend fun getProfileSync(): BabyProfile?

    @Upsert
    suspend fun upsertProfile(profile: BabyProfile)
}

// ─── Database ───────────────────────────────────────────

@Database(
    entities = [BabyProfile::class, FeedingRecord::class, ExcreteRecord::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun babyDao(): BabyDao
    abstract fun feedingDao(): FeedingDao
    abstract fun excreteDao(): ExcreteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 表结构无变化，无需执行SQL
                android.util.Log.w("AppDatabase", "Migration 1->2: schema unchanged")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "babycare_db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
