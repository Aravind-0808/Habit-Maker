package com.aravind.habitmaker

import com.aravind.habitmaker.core.reminders.HabitReminders
import com.aravind.habitmaker.core.util.Clock
import com.aravind.habitmaker.core.util.daysLabel
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatTimer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class FormatAndReminderTest {
    @Test fun durations() {
        assertEquals("0m", formatDuration(0))
        assertEquals("45m", formatDuration(45 * 60_000L))
        assertEquals("2h", formatDuration(120 * 60_000L))
        assertEquals("2h 15m", formatDuration(135 * 60_000L))
        assertEquals("07:05", formatClock(7 * 60 + 5))
        assertEquals("24:59", formatTimer(24 * 60 + 59))
        assertEquals("Weekdays", daysLabel(0b0011111))
        assertEquals("Mon, Wed", daysLabel(0b101))
    }

    @Test fun twelveHourClock() {
        try {
            Clock.use24 = false
            assertEquals("12:00 AM", formatClock(0))
            assertEquals("5:15 AM", formatClock(5 * 60 + 15))
            assertEquals("12:30 PM", formatClock(12 * 60 + 30))
            assertEquals("10:00 PM", formatClock(22 * 60))
        } finally {
            Clock.use24 = true
        }
        assertEquals("22:00", formatClock(22 * 60))
    }

    @Test fun nextReminderLaterToday() {
        val now = LocalDateTime.of(2026, 11, 11, 7, 0) // Wednesday
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 0), HabitReminders.nextTime(0b1111111, 8 * 60, now))
    }

    @Test fun nextReminderSkipsToScheduledDay() {
        val now = LocalDateTime.of(2026, 11, 11, 9, 0) // Wednesday, after 08:00
        // Only Friday (bit 4)
        assertEquals(LocalDateTime.of(2026, 11, 13, 8, 0), HabitReminders.nextTime(1 shl 4, 8 * 60, now))
    }

    @Test fun nextReminderSameWeekdayNextWeek() {
        val now = LocalDateTime.of(2026, 11, 11, 9, 0) // Wednesday (bit 2)
        assertEquals(LocalDateTime.of(2026, 11, 18, 8, 0), HabitReminders.nextTime(1 shl 2, 8 * 60, now))
    }
}
