package com.aravind.habitmaker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY createdAt")
    fun observeActive(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observe(id: Long): Flow<Habit?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): Habit?

    @Query("SELECT * FROM habits")
    suspend fun all(): List<Habit>

    @Upsert
    suspend fun upsert(habit: Habit): Long

    @Delete
    suspend fun delete(habit: Habit)

    @Query("DELETE FROM habits")
    suspend fun clear()
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE day = :day")
    fun observeDay(day: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE day >= :from")
    fun observeSince(from: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY day")
    fun observeForHabit(habitId: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE day = :day")
    suspend fun forDay(day: Long): List<HabitLog>

    @Query("SELECT * FROM habit_logs")
    suspend fun all(): List<HabitLog>

    @Upsert
    suspend fun upsert(log: HabitLog)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND day = :day")
    suspend fun delete(habitId: Long, day: Long)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)

    @Query("DELETE FROM habit_logs")
    suspend fun clear()
}

@Dao
interface FocusDao {
    @Insert
    suspend fun insert(session: FocusSession): Long

    @Query("SELECT * FROM focus_sessions ORDER BY start DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE start >= :from ORDER BY start")
    fun observeSince(from: Long): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE start >= :from ORDER BY start")
    suspend fun since(from: Long): List<FocusSession>

    @Query("SELECT * FROM focus_sessions")
    suspend fun all(): List<FocusSession>

    @Query("DELETE FROM focus_sessions")
    suspend fun clear()
}

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules")
    fun observeAll(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules")
    suspend fun all(): List<AppRule>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    suspend fun get(pkg: String): AppRule?

    @Upsert
    suspend fun upsert(rule: AppRule)

    @Query("DELETE FROM app_rules")
    suspend fun clear()
}

@Dao
interface BlockedSiteDao {
    @Query("SELECT * FROM blocked_sites ORDER BY domain")
    fun observeAll(): Flow<List<BlockedSite>>

    @Query("SELECT * FROM blocked_sites")
    suspend fun all(): List<BlockedSite>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(site: BlockedSite)

    @Delete
    suspend fun delete(site: BlockedSite)

    @Query("DELETE FROM blocked_sites")
    suspend fun clear()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM block_schedules ORDER BY start")
    fun observeAll(): Flow<List<BlockSchedule>>

    @Query("SELECT * FROM block_schedules WHERE id = :id")
    suspend fun get(id: Long): BlockSchedule?

    @Query("SELECT * FROM block_schedules")
    suspend fun all(): List<BlockSchedule>

    @Upsert
    suspend fun upsert(schedule: BlockSchedule): Long

    @Delete
    suspend fun delete(schedule: BlockSchedule)

    @Query("DELETE FROM block_schedules")
    suspend fun clear()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM exam_plan WHERE id = 1")
    fun observePlan(): Flow<ExamPlan?>

    @Query("SELECT * FROM exam_plan WHERE id = 1")
    suspend fun plan(): ExamPlan?

    @Upsert
    suspend fun savePlan(plan: ExamPlan)

    @Query("DELETE FROM exam_plan")
    suspend fun clearPlan()

    @Query("SELECT * FROM routine_blocks ORDER BY start")
    fun observeBlocks(): Flow<List<RoutineBlock>>

    @Query("SELECT * FROM routine_blocks ORDER BY start")
    suspend fun blocks(): List<RoutineBlock>

    @Query("SELECT * FROM routine_blocks WHERE id = :id")
    suspend fun block(id: Long): RoutineBlock?

    @Upsert
    suspend fun upsertBlock(block: RoutineBlock): Long

    @Insert
    suspend fun insertBlocks(blocks: List<RoutineBlock>)

    @Delete
    suspend fun deleteBlock(block: RoutineBlock)

    @Query("DELETE FROM routine_blocks")
    suspend fun clearBlocks()

    @Query("SELECT * FROM routine_logs WHERE day >= :from")
    fun observeLogsSince(from: Long): Flow<List<RoutineLog>>

    @Query("SELECT * FROM routine_logs")
    suspend fun logs(): List<RoutineLog>

    @Upsert
    suspend fun upsertLog(log: RoutineLog)

    @Query("DELETE FROM routine_logs WHERE day = :day AND blockId = :blockId")
    suspend fun deleteLog(day: Long, blockId: Long)

    @Query("DELETE FROM routine_logs WHERE blockId = :blockId")
    suspend fun deleteLogsFor(blockId: Long)

    @Query("DELETE FROM routine_logs")
    suspend fun clearLogs()
}
