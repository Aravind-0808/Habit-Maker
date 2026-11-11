package com.aravind.habitmaker

import com.aravind.habitmaker.core.routine.BlockType
import com.aravind.habitmaker.core.routine.DayResult
import com.aravind.habitmaker.core.routine.DayStatus
import com.aravind.habitmaker.core.routine.Profile
import com.aravind.habitmaker.core.routine.RoutineGenerator
import com.aravind.habitmaker.core.routine.RoutineInput
import com.aravind.habitmaker.core.routine.TEMPLATES
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.data.db.RoutineLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTest {
    private fun h(hh: Int, mm: Int = 0) = hh * 60 + mm

    @Test fun userExample_wake5_refresh515_study6_45and15() {
        val g = RoutineGenerator.generate(RoutineInput(Profile.STUDENT, wake = h(5), sleep = h(22), studyMin = 180, sessionMin = 45, breakMin = 15))
        val lines = g.blocks.map { "${formatClock(it.start)} ${it.type}" }
        assertEquals("05:00 WAKE", lines[0])
        assertEquals("05:15 REFRESH", lines[1])
        assertEquals("06:00 STUDY", lines[2])
        assertEquals("06:45 BREAK", lines[3])
        assertEquals("07:00 STUDY", lines[4])
        val study = g.blocks.filter { it.type == BlockType.STUDY }
        assertEquals(4, study.size)
        assertEquals(180, study.sumOf { it.end - it.start })
        assertEquals(h(9, 45), study.last().end)
        assertEquals(0, g.unplaced)
        assertEquals(BlockType.SLEEP, g.blocks.last().type)
    }

    @Test fun longBreakAfterThreeHours() {
        val g = RoutineGenerator.generate(RoutineInput(Profile.STUDENT, wake = h(5), sleep = h(22), studyMin = 360, sessionMin = 45, breakMin = 15))
        assertTrue(g.blocks.any { it.type == BlockType.MEAL })
        assertEquals(360, g.blocks.filter { it.type == BlockType.STUDY }.sumOf { it.end - it.start })
    }

    @Test fun workingStudiesOutsideWork() {
        val g = RoutineGenerator.generate(RoutineInput(Profile.WORKING, wake = h(6), sleep = h(23), workStart = h(9, 30), workEnd = h(18), studyMin = 180, sessionMin = 50, breakMin = 10))
        val study = g.blocks.filter { it.type == BlockType.STUDY }
        assertTrue(study.none { it.start < h(18) && it.end > h(9, 30) })
        assertEquals(180, study.sumOf { it.end - it.start })
        assertTrue(g.blocks.any { it.type == BlockType.WORK })
    }

    @Test fun eveningFirstFillsEveningBeforeMorning() {
        val g = RoutineGenerator.generate(RoutineInput(Profile.WORKING, wake = h(6, 30), sleep = h(23), studyMin = 100, sessionMin = 50, breakMin = 10, eveningFirst = true))
        assertTrue(g.blocks.filter { it.type == BlockType.STUDY }.all { it.start >= h(19) })
    }

    @Test fun tooMuchStudyReportsUnplaced() {
        val g = RoutineGenerator.generate(RoutineInput(Profile.WORKING, wake = h(7), sleep = h(22), workStart = h(8), workEnd = h(20), studyMin = 600, sessionMin = 45, breakMin = 15))
        assertTrue(g.unplaced > 0)
    }

    @Test fun sleepAfterMidnight() {
        val g = RoutineGenerator.generate(RoutineInput(Profile.STUDENT, wake = h(8), sleep = h(0, 30), studyMin = 240, sessionMin = 50, breakMin = 10))
        val sleep = g.blocks.first { it.type == BlockType.SLEEP }
        assertEquals(h(0, 30), sleep.start)
        assertEquals(h(8), sleep.end)
        assertEquals(0, g.unplaced)
    }

    @Test fun everyTemplateFits() {
        TEMPLATES.forEach { assertEquals(it.name, 0, RoutineGenerator.generate(it.input).unplaced) }
    }

    @Test fun dayStatusAndStreak() {
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        fun logs(vararg done: Long) = done.map { RoutineLog(0, it, RoutineLog.DONE) }
        val all = (ids + listOf(10L, 11L)).toSet() // 10, 11 = wake-up / break blocks
        assertEquals(DayStatus.DONE, DayResult.status(ids, all, logs(1, 2, 3), isToday = false, isFuture = false))
        assertEquals(DayStatus.MISSED, DayResult.status(ids, all, logs(1, 2), isToday = false, isFuture = false))
        assertEquals(DayStatus.MISSED, DayResult.status(ids, all, logs(1, 10, 11), isToday = false, isFuture = false)) // non-study don't count
        assertEquals(DayStatus.DONE, DayResult.status(ids, all, logs(1, 98, 99), isToday = false, isFuture = false)) // old routine's blocks do
        assertEquals(DayStatus.IN_PROGRESS, DayResult.status(ids, all, logs(1), isToday = true, isFuture = false))
        assertEquals(DayStatus.NONE, DayResult.status(emptyList(), all, emptyList(), isToday = false, isFuture = false))
        // today in progress, then 2 done, a day off, 1 done, then missed
        val desc = listOf(DayStatus.IN_PROGRESS, DayStatus.DONE, DayStatus.DONE, DayStatus.NONE, DayStatus.DONE, DayStatus.MISSED, DayStatus.DONE)
        assertEquals(3, DayResult.streak(desc))
    }
}
