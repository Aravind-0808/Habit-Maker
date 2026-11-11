package com.aravind.habitmaker.core.util

import java.time.LocalDate

data class StreakInfo(val current: Int, val best: Int, val totalDone: Int)

/**
 * Streaks count only the habit's scheduled days; other days are skipped.
 * If today is scheduled but not done yet, the streak still counts from yesterday.
 *
 * @param doneDays epoch days on which the habit reached its target
 * @param days     weekday mask (Monday = bit 0)
 */
object Streaks {
    fun compute(doneDays: Set<Long>, days: Int, firstDay: LocalDate, today: LocalDate): StreakInfo {
        fun scheduled(d: LocalDate) = days and Dates.bit(d.dayOfWeek) != 0
        if (days == 0) return StreakInfo(0, 0, doneDays.size)

        var current = 0
        var d = today
        if (scheduled(d) && d.toEpochDay() !in doneDays) d = d.minusDays(1)
        while (!d.isBefore(firstDay)) {
            if (scheduled(d)) {
                if (d.toEpochDay() in doneDays) current++ else break
            }
            d = d.minusDays(1)
        }

        var best = 0
        var run = 0
        d = firstDay
        while (!d.isAfter(today)) {
            if (scheduled(d)) {
                if (d.toEpochDay() in doneDays) { run++; best = maxOf(best, run) }
                else if (d != today) run = 0
            }
            d = d.plusDays(1)
        }
        return StreakInfo(current, maxOf(best, current), doneDays.size)
    }
}
