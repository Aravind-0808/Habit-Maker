package com.aravind.habitmaker.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.aravind.habitmaker.core.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class DayUsage(
    val day: LocalDate,
    val totalMs: Long,
    /** Package → foreground time, largest first. */
    val byApp: List<Pair<String, Long>>,
    /** Screen unlocks, or -1 when the phone cannot tell (Android 8.1 and older). */
    val unlocks: Int,
)

/** Screen time and unlocks from Android's usage stats (needs "Usage access"). */
class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Home-screen apps and system UI are not counted as screen time. */
    private val ignored: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet() +
            setOf("com.android.systemui", context.packageName)
    }

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun day(day: LocalDate): DayUsage = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext DayUsage(day, 0, emptyList(), -1)
        val (start, end) = Dates.range(day)
        var (byApp, unlocks) = scan(start, minOf(end, System.currentTimeMillis()))
        // Android keeps detailed events only for about a week; older days fall back to its daily totals.
        if (byApp.isEmpty() && day.isBefore(LocalDate.now().minusDays(6))) {
            byApp = daily(start, end)
            unlocks = -1
        }
        DayUsage(day, byApp.values.sum(), byApp.toList().sortedByDescending { it.second }, unlocks)
    }

    suspend fun today() = day(LocalDate.now())

    /** The last [count] days, oldest first, today last. */
    suspend fun lastDays(count: Int = 7): List<DayUsage> {
        val today = LocalDate.now()
        return (count - 1 downTo 0).map { day(today.minusDays(it.toLong())) }
    }

    private fun daily(start: Long, end: Long): Map<String, Long> =
        usm.queryAndAggregateUsageStats(start, end)
            .filterKeys { it !in ignored }
            .mapValues { it.value.totalTimeInForeground }
            .filterValues { it > 0 }

    /** Foreground time per app and the number of unlocks between [start] and [end]. */
    private fun scan(start: Long, end: Long): Pair<Map<String, Long>, Int> {
        val totals = HashMap<String, Long>()
        var unlocks = 0
        var current: String? = null
        var since = 0L
        fun close(at: Long) {
            val pkg = current ?: return
            if (pkg !in ignored && at > since) totals[pkg] = (totals[pkg] ?: 0L) + (at - since)
            current = null
        }
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                RESUMED -> { close(e.timeStamp); current = e.packageName; since = e.timeStamp }
                PAUSED -> if (e.packageName == current) close(e.timeStamp)
                SCREEN_OFF, KEYGUARD_SHOWN -> close(e.timeStamp)
                KEYGUARD_HIDDEN -> unlocks++
            }
        }
        close(end)
        return totals to (if (Build.VERSION.SDK_INT >= 28) unlocks else -1)
    }

    private companion object {
        // UsageEvents.Event constants (some are API 28+, the numbers are stable).
        const val RESUMED = 1
        const val PAUSED = 2
        const val SCREEN_OFF = 16
        const val KEYGUARD_SHOWN = 17
        const val KEYGUARD_HIDDEN = 18
    }
}
