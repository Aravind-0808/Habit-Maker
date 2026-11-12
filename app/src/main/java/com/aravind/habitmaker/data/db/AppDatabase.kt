package com.aravind.habitmaker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Habit::class, HabitLog::class, FocusSession::class, AppRule::class, BlockedSite::class, BlockSchedule::class,
        ExamPlan::class, RoutineBlock::class, RoutineLog::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habits(): HabitDao
    abstract fun habitLogs(): HabitLogDao
    abstract fun focus(): FocusDao
    abstract fun appRules(): AppRuleDao
    abstract fun sites(): BlockedSiteDao
    abstract fun schedules(): ScheduleDao
    abstract fun routine(): RoutineDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "habitmaker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** v2: several block schedules (was one schedule in settings). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `block_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `days` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)",
                )
            }
        }

        /** v3: exam prep — plan, routine blocks and their done/missed log. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exam_plan` (`id` INTEGER NOT NULL, `exam` TEXT NOT NULL, `examDay` INTEGER, " +
                        "`profile` TEXT NOT NULL, `wake` INTEGER NOT NULL, `sleep` INTEGER NOT NULL, `workStart` INTEGER NOT NULL, " +
                        "`workEnd` INTEGER NOT NULL, `studyMin` INTEGER NOT NULL, `sessionMin` INTEGER NOT NULL, `breakMin` INTEGER NOT NULL, " +
                        "`autoFocus` INTEGER NOT NULL, `strictFocus` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `days` INTEGER NOT NULL, `remind` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine_logs` (`day` INTEGER NOT NULL, `blockId` INTEGER NOT NULL, " +
                        "`status` INTEGER NOT NULL, PRIMARY KEY(`day`, `blockId`))",
                )
            }
        }

        /** v5: optional habit timing, preserving all existing habits and logs. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `timing` TEXT NOT NULL DEFAULT 'ANYTIME'")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `startMinute` INTEGER")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `endMinute` INTEGER")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `intervalMin` INTEGER")
            }
        }

        /** v4: YouTube / Instagram can block only Shorts / Reels. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_rules` ADD COLUMN `shortsOnly` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
