package com.aravind.habitmaker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Days are a bit mask: bit 0 = Monday … bit 6 = Sunday. */
const val ALL_DAYS = 0b1111111

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** ARGB colour. */
    val color: Long,
    val days: Int = ALL_DAYS,
    /** Minute of the day for the reminder, or null for none. */
    val reminderMinute: Int? = null,
    /** Times per day to count as done. */
    val target: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

/** How many times a habit was done on a day ([day] = LocalDate.toEpochDay()). */
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "day"])
data class HabitLog(val habitId: Long, val day: Long, val count: Int)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long,
    val plannedMin: Int,
    val actualMin: Int,
    val completed: Boolean,
    val label: String,
)

/** Per-app blocking rules. */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    /** Blocked during focus, "block now" and the schedule. */
    val distracting: Boolean = false,
    /** Daily limit in minutes, 0 = none. */
    val dailyLimitMin: Int = 0,
    /** YouTube / Instagram: when blocking is on, block only Shorts / Reels instead of the whole app. */
    @ColumnInfo(defaultValue = "0") val shortsOnly: Boolean = false,
)

@Entity(tableName = "blocked_sites")
data class BlockedSite(@PrimaryKey val domain: String)

/** A time window when distracting apps are blocked. Windows may cross midnight. */
@Entity(tableName = "block_schedules")
data class BlockSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Minute of the day. */
    val start: Int,
    val end: Int,
    val days: Int = ALL_DAYS,
    val enabled: Boolean = true,
)

/** The exam plan (one row, id = 1): what the routine was built from. */
@Entity(tableName = "exam_plan")
data class ExamPlan(
    @PrimaryKey val id: Int = 1,
    val exam: String,
    /** Exam date as epoch day, or null. */
    val examDay: Long?,
    /** STUDENT, WORKING or PART_TIME. */
    val profile: String,
    val wake: Int,
    val sleep: Int,
    val workStart: Int,
    val workEnd: Int,
    /** Study minutes per day. */
    val studyMin: Int,
    val sessionMin: Int,
    val breakMin: Int,
    /** Study blocks start the focus timer (and app blocking) by themselves. */
    val autoFocus: Boolean,
    val strictFocus: Boolean,
)

/** One block of the daily routine: wake up, study, break… Times are minutes of the day; may cross midnight. */
@Entity(tableName = "routine_blocks")
data class RoutineBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** A [com.aravind.habitmaker.core.routine.BlockType] name. */
    val type: String,
    val title: String,
    val start: Int,
    val end: Int,
    val days: Int = ALL_DAYS,
    /** Notify at the start (for WAKE: ring the alarm). */
    val remind: Boolean = true,
)

/** Done (1) or missed (2) for a block on a day. */
@Entity(tableName = "routine_logs", primaryKeys = ["day", "blockId"])
data class RoutineLog(val day: Long, val blockId: Long, val status: Int) {
    companion object {
        const val DONE = 1
        const val MISSED = 2
    }
}
