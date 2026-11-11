package com.aravind.habitmaker.core.routine

import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.data.db.RoutineLog

enum class BlockType(val label: String, val isStudy: Boolean = false) {
    WAKE("Wake up"),
    REFRESH("Refresh"),
    STUDY("Study", isStudy = true),
    REVISION("Revision", isStudy = true),
    MOCK_TEST("Mock test", isStudy = true),
    BREAK("Break"),
    MEAL("Meal / long break"),
    EXERCISE("Exercise"),
    WORK("Work / college"),
    SLEEP("Sleep"),
    CUSTOM("Other");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}

enum class Profile(val label: String, val detail: String) {
    STUDENT("Full-time student", "Free most of the day"),
    WORKING("Working", "Study before and after work"),
    PART_TIME("Part-time", "Some busy hours in the day"),
}

val EXAMS = listOf("UGC NET", "UPSC", "SSC", "Bank exams", "GATE", "NEET", "JEE", "CAT", "State PSC")

/** Everything the routine is generated from. Times are minutes of the day. */
data class RoutineInput(
    val profile: Profile,
    val wake: Int,
    val sleep: Int,
    /** Busy hours (work / college). Ignored for students. */
    val workStart: Int = 9 * 60 + 30,
    val workEnd: Int = 18 * 60,
    val studyMin: Int,
    val sessionMin: Int,
    val breakMin: Int,
    /** Working: fill the evening before the morning. */
    val eveningFirst: Boolean = false,
)

data class Draft(val type: BlockType, val title: String, val start: Int, val end: Int)

data class Generated(val blocks: List<Draft>, /** Study minutes that did not fit. */ val unplaced: Int)

data class Template(val name: String, val detail: String, val input: RoutineInput)

val TEMPLATES = listOf(
    Template("Full-day student", "6 h study · 45 min + 15 min break",
        RoutineInput(Profile.STUDENT, wake = 5 * 60, sleep = 22 * 60, studyMin = 360, sessionMin = 45, breakMin = 15)),
    Template("Working – morning", "2 h before work · 45 + 15",
        RoutineInput(Profile.WORKING, wake = 5 * 60, sleep = 22 * 60 + 30, studyMin = 120, sessionMin = 45, breakMin = 15)),
    Template("Working – evening", "3 h after work · 50 + 10",
        RoutineInput(Profile.WORKING, wake = 6 * 60 + 30, sleep = 23 * 60, studyMin = 180, sessionMin = 50, breakMin = 10, eveningFirst = true)),
    Template("Weekend intensive", "8 h study · 50 + 10",
        RoutineInput(Profile.STUDENT, wake = 6 * 60, sleep = 23 * 60, studyMin = 480, sessionMin = 50, breakMin = 10)),
    Template("Pomodoro", "4 h · 25 min + 5 min break",
        RoutineInput(Profile.STUDENT, wake = 6 * 60, sleep = 22 * 60 + 30, studyMin = 240, sessionMin = 25, breakMin = 5)),
)

/**
 * Builds a day: wake up, refresh, then study sessions with breaks inside the
 * free windows, a long break after every 3 hours of study, and sleep.
 *
 * Example (student, wake 5:00, 45/15): 5:00 wake · 5:15 refresh · 6:00 study ·
 * 6:45 break · 7:00 study …
 */
object RoutineGenerator {
    private const val DAY = 24 * 60
    private const val LONG_BREAK = 45
    private const val LONG_BREAK_EVERY = 180

    fun generate(i: RoutineInput): Generated {
        val out = mutableListOf<Draft>()
        // Work in "minutes since wake-up" so a sleep after midnight still works.
        fun abs(m: Int) = if (m < i.wake) m + DAY else m
        val wake = i.wake
        val sleep = abs(i.sleep).let { if (it <= wake) it + DAY else it }

        out += Draft(BlockType.WAKE, "Wake up", wake, wake + 15)
        out += Draft(BlockType.REFRESH, "Refresh", wake + 15, wake + 45)

        val dayEnd = sleep - 30
        val windows = mutableListOf<IntRange>()
        if (i.profile == Profile.STUDENT) {
            windows += (wake + 60)..dayEnd
        } else {
            val ws = abs(i.workStart)
            val we = abs(i.workEnd).let { if (it < ws) it + DAY else it }
            out += Draft(BlockType.WORK, if (i.profile == Profile.WORKING) "Work" else "Busy", ws, we)
            val morning = (wake + 60)..(ws - 30)
            val evening = (we + 60)..dayEnd
            val list = listOf(morning, evening).filter { it.last - it.first >= 25 }
            windows += if (i.eveningFirst) list.reversed() else list
        }

        var remaining = i.studyMin
        var session = 0
        var sinceLong = 0
        val studies = mutableListOf<Draft>()
        for (w in windows) {
            var t = w.first
            while (remaining > 0) {
                val len = minOf(i.sessionMin, remaining)
                if (t + len > w.last) break
                session++
                studies += Draft(BlockType.STUDY, "Study session $session", t, t + len)
                t += len
                remaining -= len
                sinceLong += len
                if (remaining <= 0) break
                val pause = if (sinceLong >= LONG_BREAK_EVERY) LONG_BREAK else i.breakMin
                if (t + pause + minOf(i.sessionMin, remaining) > w.last) break
                studies += Draft(if (pause == LONG_BREAK) BlockType.MEAL else BlockType.BREAK, if (pause == LONG_BREAK) "Long break" else "Break", t, t + pause)
                t += pause
                if (pause == LONG_BREAK) sinceLong = 0
            }
        }
        out += studies
        out += Draft(BlockType.SLEEP, "Sleep", sleep, wake + DAY)

        val day = out.sortedBy { it.start }.map { it.copy(start = it.start % DAY, end = it.end % DAY) }
        return Generated(day, remaining.coerceAtLeast(0))
    }

    fun toBlocks(drafts: List<Draft>): List<RoutineBlock> =
        drafts.map { RoutineBlock(type = it.type.name, title = it.title, start = it.start, end = it.end, remind = it.type != BlockType.SLEEP && it.type != BlockType.WORK) }
}

enum class DayStatus { DONE, MISSED, IN_PROGRESS, NONE }

object DayResult {
    /** A day counts as done when at least this share of its study blocks is done. */
    const val DONE_SHARE = 0.6f

    /**
     * @param studyBlockIds the study-type blocks scheduled that day
     * @param allBlockIds   every block in the routine now; a log for a block that no longer
     *                      exists (the routine was rebuilt) still counts as a study block done
     * @param logs          that day's logs
     */
    fun status(studyBlockIds: List<Long>, allBlockIds: Set<Long>, logs: List<RoutineLog>, isToday: Boolean, isFuture: Boolean): DayStatus {
        if (studyBlockIds.isEmpty() || isFuture) return DayStatus.NONE
        val done = logs.count { it.status == RoutineLog.DONE && (it.blockId in studyBlockIds || it.blockId !in allBlockIds) }
            .coerceAtMost(studyBlockIds.size)
        val share = done.toFloat() / studyBlockIds.size
        return when {
            share >= DONE_SHARE -> DayStatus.DONE
            isToday -> DayStatus.IN_PROGRESS
            else -> DayStatus.MISSED
        }
    }

    /** Consecutive DONE days ending today (today may still be in progress). */
    fun streak(statusByDayDesc: List<DayStatus>): Int {
        var n = 0
        for ((i, s) in statusByDayDesc.withIndex()) {
            when (s) {
                DayStatus.DONE -> n++
                DayStatus.IN_PROGRESS, DayStatus.NONE -> if (i == 0 || s == DayStatus.NONE) continue else break
                DayStatus.MISSED -> break
            }
        }
        return n
    }
}

/** Duration of a block in minutes (handles blocks that cross midnight). */
fun RoutineBlock.minutes(): Int = ((end - start) + 24 * 60) % (24 * 60)

/** True if [minuteOfDay] is inside the block. */
fun RoutineBlock.contains(minuteOfDay: Int): Boolean =
    if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end

val RoutineBlock.kind get() = BlockType.of(type)
