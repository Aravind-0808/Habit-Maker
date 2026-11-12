package com.aravind.habitmaker

import com.aravind.habitmaker.core.reminders.HabitReminders
import com.aravind.habitmaker.data.db.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class HabitTimingTest {
    private val water = Habit(name = "Water", icon = "water", color = 0,
        timing = "REPEAT", startMinute = 8 * 60, endMinute = 20 * 60,
        intervalMin = 120, reminderMinute = 8 * 60, target = 6)
    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 11, hour, minute)

    @Test fun repeatStartsAtWindowOpening() {
        assertEquals(at(8), HabitReminders.nextTime(water, at(7)))
    }
    @Test fun repeatAdvancesStrictlyPastTheCurrentSlot() {
        assertEquals(at(10), HabitReminders.nextTime(water, at(8)))
        assertEquals(at(10), HabitReminders.nextTime(water, at(9, 59)))
    }
    @Test fun lastSlotIsIncludedAndThenMovesToTomorrow() {
        assertEquals(at(20), HabitReminders.nextTime(water, at(19)))
        assertEquals(at(8).plusDays(1), HabitReminders.nextTime(water, at(20)))
    }
    @Test fun unalignedEndDoesNotCreateAnExtraReminder() {
        val h = water.copy(endMinute = 19 * 60)
        assertEquals(at(8).plusDays(1), HabitReminders.nextTime(h, at(18)))
    }
    @Test fun repeatSkipsUnselectedDays() {
        assertEquals(at(8).plusDays(2), HabitReminders.nextTime(water.copy(days = 1 shl 4), at(7)))
    }
    @Test fun disabledAndArchivedHabitsDoNotSchedule() {
        assertNull(HabitReminders.nextTime(water.copy(reminderMinute = null), at(7)))
        assertNull(HabitReminders.nextTime(water.copy(archived = true), at(7)))
        assertNull(HabitReminders.nextTime(water.copy(days = 0), at(7)))
    }
    @Test fun invalidRepeatSettingsDoNotSchedule() {
        assertNull(HabitReminders.nextTime(water.copy(intervalMin = 0), at(7)))
        assertNull(HabitReminders.nextTime(water.copy(endMinute = 6 * 60), at(7)))
        assertNull(HabitReminders.nextTime(water.copy(reminderMinute = 1440), at(7)))
    }
    @Test fun timeWindowRemindsOnlyOnceAtItsStart() {
        val work = water.copy(timing = "WINDOW", startMinute = 9 * 60, reminderMinute = 9 * 60, endMinute = 17 * 60)
        assertEquals(at(9), HabitReminders.nextTime(work, at(8)))
        assertEquals(at(9).plusDays(1), HabitReminders.nextTime(work, at(9)))
    }
    @Test fun existingSingleRemindersKeepTheirSchedule() {
        val legacy = Habit(name = "Yoga", icon = "meditate", color = 0, reminderMinute = 8 * 60)
        assertEquals(at(8), HabitReminders.nextTime(legacy, at(7)))
        assertEquals(at(8).plusDays(1), HabitReminders.nextTime(legacy, at(8)))
    }
}
