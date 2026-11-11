package com.aravind.habitmaker.core.blocking

import com.aravind.habitmaker.data.db.AppRule

enum class BlockReason(val title: String, val message: String) {
    FOCUS("Focus session running", "This app is blocked until your focus session ends."),
    BLOCK_NOW("Blocking is on", "You turned on blocking for distracting apps."),
    SCHEDULE("Scheduled block", "This app is blocked during your scheduled hours."),
    LIMIT("Daily limit reached", "You've used up today's time for this app."),
    SHORTS("Short videos blocked", "Shorts and Reels are blocked. The rest of the app still works."),
    WEBSITE("Website blocked", "You added this website to your block list."),
    PROTECT("Strict mode is on", "Habit Maker can't be turned off or uninstalled until your strict session ends."),
}

/** One block schedule: [start]/[end] are minutes of the day, [days] a weekday mask (Monday = bit 0). */
data class ScheduleWindow(val start: Int, val end: Int, val days: Int)

/** Everything the blocker needs to decide, as plain values so it can be unit-tested. */
data class PolicyInput(
    val rules: Map<String, AppRule>,
    val focusRunning: Boolean,
    val blockDuringFocus: Boolean,
    val blockNowUntil: Long,
    /** Enabled schedules only. */
    val schedules: List<ScheduleWindow>,
    val nowMs: Long,
    /** 0..1439 */
    val minuteOfDay: Int,
    /** 0 = Monday … 6 = Sunday */
    val weekday: Int,
)

object BlockPolicy {
    /** Why [pkg] should be blocked right now, or null. [usedTodayMs] is its screen time today. */
    fun decide(pkg: String, usedTodayMs: Long, input: PolicyInput): BlockReason? {
        val rule = input.rules[pkg] ?: return null
        if (rule.distracting) activePeriod(input)?.let { return it }
        if (rule.dailyLimitMin > 0 && usedTodayMs >= rule.dailyLimitMin * 60_000L) return BlockReason.LIMIT
        return null
    }

    /** Focus, "block now" or a schedule — whichever is blocking distracting apps right now. */
    fun activePeriod(input: PolicyInput): BlockReason? = when {
        input.focusRunning && input.blockDuringFocus -> BlockReason.FOCUS
        input.blockNowUntil > input.nowMs -> BlockReason.BLOCK_NOW
        input.schedules.any { scheduleActive(it, input.minuteOfDay, input.weekday) } -> BlockReason.SCHEDULE
        else -> null
    }

    /** Should [pkg]'s Shorts / Reels be blocked now: always (global switch) or during a blocking period (per app). */
    fun blockShortsNow(pkg: String, alwaysBlockShorts: Boolean, input: PolicyInput): Boolean =
        alwaysBlockShorts || (input.rules[pkg]?.shortsOnly == true && activePeriod(input) != null)

    /** True inside the schedule window. Windows may cross midnight (22:00 → 07:00). */
    fun scheduleActive(w: ScheduleWindow, minuteOfDay: Int, weekday: Int): Boolean {
        fun on(day: Int) = w.days and (1 shl ((day + 7) % 7)) != 0
        val m = minuteOfDay
        return if (w.start <= w.end) {
            on(weekday) && m >= w.start && m < w.end
        } else {
            // The late part belongs to today, the early-morning part to yesterday's window.
            (m >= w.start && on(weekday)) || (m < w.end && on(weekday - 1))
        }
    }

    /** True if [host] is [domain] or one of its subdomains. */
    fun matchesSite(host: String, domain: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        val d = domain.lowercase().removePrefix("www.")
        return h == d || h.endsWith(".$d")
    }

    /** Pulls the host out of what a browser's address bar shows ("m.youtube.com/watch…" → "m.youtube.com"). */
    fun hostOf(urlText: String): String? {
        val t = urlText.trim().lowercase()
        if (t.isEmpty() || ' ' in t) return null
        val noScheme = t.substringAfter("://")
        val host = noScheme.substringBefore('/').substringBefore('?').substringBefore(':')
        return host.takeIf { '.' in it }
    }

    /** Cleans what the user typed into a domain ("https://www.Reddit.com/r/x" → "reddit.com"). */
    fun normalizeDomain(input: String): String? = hostOf(input)?.removePrefix("www.")
}
