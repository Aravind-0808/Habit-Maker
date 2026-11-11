package com.aravind.habitmaker.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Dates {
    fun today(): Long = LocalDate.now().toEpochDay()

    /** Start and end (ms) of a day in the phone's time zone. */
    fun range(day: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        return day.atStartOfDay(zone).toInstant().toEpochMilli() to
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun startOfToday(): Long = range(LocalDate.now()).first

    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Bit for a weekday in a days mask (Monday = bit 0). */
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    val SHORT_DAYS = listOf("M", "T", "W", "T", "F", "S", "S")
    val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}

/** 2h 15m · 45m · 0m */
fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun formatMinutes(min: Int): String = formatDuration(min * 60_000L)

/** 12- or 24-hour clock for the whole app. Compose state, so screens update when it changes. */
object Clock {
    var use24 by mutableStateOf(true)
}

/** Minute of the day → 07:30 (24 h) or 7:30 AM (12 h). */
fun formatClock(minuteOfDay: Int): String {
    val h = (minuteOfDay / 60) % 24
    val m = minuteOfDay % 60
    if (Clock.use24) return "%02d:%02d".format(h, m)
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}

fun formatTime(t: LocalTime): String = formatClock(t.hour * 60 + t.minute)

/** Seconds → 24:59 */
fun formatTimer(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

fun daysLabel(mask: Int): String = when (mask) {
    0b1111111 -> "Every day"
    0b0011111 -> "Weekdays"
    0b1100000 -> "Weekends"
    else -> (0..6).filter { mask and (1 shl it) != 0 }.joinToString(", ") { Dates.DAY_NAMES[it] }
}
