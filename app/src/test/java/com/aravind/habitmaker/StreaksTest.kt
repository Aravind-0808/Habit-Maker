package com.aravind.habitmaker

import com.aravind.habitmaker.core.util.Streaks
import com.aravind.habitmaker.data.db.ALL_DAYS
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 11, 11) // Wednesday
    private fun days(vararg back: Int) = back.map { today.minusDays(it.toLong()).toEpochDay() }.toSet()

    @Test fun countsBackFromToday() {
        val s = Streaks.compute(days(0, 1, 2), ALL_DAYS, today.minusDays(10), today)
        assertEquals(3, s.current)
    }

    @Test fun todayNotDoneYetKeepsStreak() {
        val s = Streaks.compute(days(1, 2), ALL_DAYS, today.minusDays(10), today)
        assertEquals(2, s.current)
    }

    @Test fun gapBreaksStreakBestRemembered() {
        val s = Streaks.compute(days(0, 3, 4, 5, 6), ALL_DAYS, today.minusDays(10), today)
        assertEquals(1, s.current)
        assertEquals(4, s.best)
        assertEquals(5, s.totalDone)
    }

    @Test fun unscheduledDaysAreSkipped() {
        // Weekdays only. Today Wed; done Mon, Tue, Wed and previous Fri → weekend skipped.
        val weekdays = 0b0011111
        val s = Streaks.compute(days(0, 1, 2, 5), weekdays, today.minusDays(10), today)
        assertEquals(4, s.current)
    }
}
