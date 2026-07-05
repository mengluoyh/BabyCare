package com.babycare.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ─── 实体 ───────────────────────────────────────────────

@Entity(tableName = "baby_profile")
data class BabyProfile(
    @PrimaryKey val id: Int = 1,
    val birthDate: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val weight: Float = 0f,        // 体重(kg)，0表示未设置
    val weightLocked: Boolean = false
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

    @Query("SELECT * FROM feeding_records ORDER BY timestamp DESC")
    suspend fun getAllSnapshot(): List<FeedingRecord>

    @Query("SELECT * FROM feeding_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): FeedingRecord?

    @Query("SELECT * FROM feeding_records WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp")
    suspend fun getFeedingsBetween(start: Long, end: Long): List<FeedingRecord>

    @Query("SELECT * FROM feeding_records WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp")
    fun getFeedingsBetweenFlow(start: Long, end: Long): Flow<List<FeedingRecord>>

    @Query("SELECT COUNT(*) FROM feeding_records WHERE feedType = 'breast' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getBreastCountBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM feeding_records WHERE feedType = 'formula' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getFormulaCountBetween(start: Long, end: Long): Int

    @Query("SELECT COALESCE(SUM(volume), 0) FROM feeding_records WHERE feedType = 'formula' AND timestamp >= :start AND timestamp <= :end")
    suspend fun getFormulaTotalBetween(start: Long, end: Long): Int

    @Query("SELECT COALESCE(SUM(volume), 0) FROM feeding_records WHERE feedType = 'formula' AND timestamp >= :start AND timestamp <= :end GROUP BY strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') ORDER BY timestamp DESC LIMIT 7")
    suspend fun getDailyFormulaLast7Days(start: Long, end: Long): List<Int>

    @Query("SELECT COUNT(*) FROM feeding_records WHERE feedType = 'breast' AND timestamp >= :start AND timestamp <= :end GROUP BY strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') ORDER BY timestamp DESC LIMIT 7")
    suspend fun getDailyBreastCountLast7Days(start: Long, end: Long): List<Int>

    @Insert
    suspend fun insert(record: FeedingRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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

    @Query("SELECT * FROM excrete_records ORDER BY timestamp DESC")
    suspend fun getAllSnapshot(): List<ExcreteRecord>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ExcreteRecord>)

    @Delete
    suspend fun delete(record: ExcreteRecord)

    @Query("DELETE FROM excrete_records")
    suspend fun deleteAll()
}

@Entity(tableName = "vaccination_records")
data class VaccinationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vaccineName: String,
    val vaccinationTime: Long,
    val nextVaccinationTime: Long? = null,
    val isLocked: Boolean = false,
    val note: String? = null
)

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weight: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface VaccineDao {
    @Upsert
    suspend fun upsert(record: VaccinationRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<VaccinationRecord>)

    @Query("SELECT * FROM vaccination_records ORDER BY vaccinationTime DESC")
    suspend fun getAllSnapshot(): List<VaccinationRecord>

    @Query("SELECT * FROM vaccination_records WHERE id = :id")
    suspend fun getById(id: Int): VaccinationRecord?

    @Delete
    suspend fun delete(record: VaccinationRecord)

    @Query("DELETE FROM vaccination_records")
    suspend fun deleteAll()
}

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(record: WeightRecord)

    @Query("SELECT * FROM weight_records ORDER BY timestamp ASC")
    suspend fun getAll(): List<WeightRecord>

    @Query("SELECT * FROM weight_records ORDER BY timestamp DESC")
    suspend fun getAllSnapshot(): List<WeightRecord>

    @Query("SELECT * FROM weight_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): WeightRecord?

    @Delete
    suspend fun delete(record: WeightRecord)
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
    entities = [BabyProfile::class, FeedingRecord::class, ExcreteRecord::class, VaccinationRecord::class, WeightRecord::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun babyDao(): BabyDao
    abstract fun feedingDao(): FeedingDao
    abstract fun excreteDao(): ExcreteDao
    abstract fun vaccineDao(): VaccineDao
    abstract fun weightDao(): WeightDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1→v2：baby_profile 移除 name/customFormulaTarget/formulaAgeUnit 列 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE baby_profile_new (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "birthDate INTEGER NOT NULL, " +
                        "isLocked INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO baby_profile_new (id, birthDate, isLocked) " +
                        "SELECT id, birthDate, isLocked FROM baby_profile")
                db.execSQL("DROP TABLE baby_profile")
                db.execSQL("ALTER TABLE baby_profile_new RENAME TO baby_profile")
                android.util.Log.w("AppDatabase", "Migration 1->2: removed name/customFormulaTarget/formulaAgeUnit")
            }
        }

        /** v2→v3：baby_profile 新增 weight/weightLocked */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby_profile ADD COLUMN weight REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE baby_profile ADD COLUMN weightLocked INTEGER NOT NULL DEFAULT 0")
                android.util.Log.w("AppDatabase", "Migration 2->3: added weight/weightLocked")
            }
        }

        /** v4→v5：新增 vaccination_records 表 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vaccination_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`vaccineName` TEXT NOT NULL, " +
                        "`vaccinationTime` INTEGER NOT NULL, " +
                        "`nextVaccinationTime` INTEGER, " +
                        "`isLocked` INTEGER NOT NULL DEFAULT 0, " +
                        "`note` TEXT)")
                android.util.Log.w("AppDatabase", "Migration 4->5: added vaccination_records table")
            }
        }

        /** v3→v4：修复之前错误的 MIGRATION_1_2（原为空操作），重建 baby_profile 移除残留列 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 重建 baby_profile，只保留当前实体定义的列
                db.execSQL("CREATE TABLE baby_profile_new (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "birthDate INTEGER NOT NULL, " +
                        "isLocked INTEGER NOT NULL DEFAULT 0, " +
                        "weight REAL NOT NULL DEFAULT 0, " +
                        "weightLocked INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO baby_profile_new (id, birthDate, isLocked, weight, weightLocked) " +
                        "SELECT id, birthDate, isLocked, weight, weightLocked FROM baby_profile")
                db.execSQL("DROP TABLE baby_profile")
                db.execSQL("ALTER TABLE baby_profile_new RENAME TO baby_profile")
                android.util.Log.w("AppDatabase", "Migration 3->4: rebuilt baby_profile to match current schema")
            }
        }

        /** v5→v6：新增 weight_records 表 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `weight_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`weight` REAL NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL DEFAULT (strftime('%s','now')*1000))")
                android.util.Log.w("AppDatabase", "Migration 5->6: added weight_records table")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "babycare_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
