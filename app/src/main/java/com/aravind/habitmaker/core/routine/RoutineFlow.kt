package com.aravind.habitmaker.core.routine

import android.content.Context
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusRun
import com.aravind.habitmaker.core.util.Dates
import java.time.LocalDateTime

/** Moves the focus timer along the routine: study → break → study … */
object RoutineFlow {
    private const val DAY = 24 * 60

    /**
     * After [done] (a routine study session or break) ran to the end, the timer that comes next:
     * the break that starts now after a study session, or the study session that starts now after
     * a break (only with auto-focus on). Null when nothing in the routine starts now.
     */
    suspend fun nextRun(context: Context, done: FocusRun): FocusRun? {
        if (done.blockId == null) return null
        val dao = context.app.db.routine()
        val plan = dao.plan() ?: return null
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        val bit = Dates.bit(now.dayOfWeek)
        val b = dao.blocks()
            .filter { it.id != done.blockId && it.days and bit != 0 && it.kind != BlockType.SLEEP && distance(it.start, minute) <= 3 }
            .minByOrNull { distance(it.start, minute) } ?: return null
        return next(done.isBreak, b, minute, plan.autoFocus, plan.strictFocus, System.currentTimeMillis())
    }

    /** Pure part of [nextRun], for tests. */
    fun next(afterBreak: Boolean, b: com.aravind.habitmaker.data.db.RoutineBlock, minute: Int, autoFocus: Boolean, strict: Boolean, nowMs: Long): FocusRun? {
        val left = ((b.end - minute + DAY) % DAY).coerceAtLeast(1)
        val isBreak = b.kind == BlockType.BREAK || b.kind == BlockType.MEAL
        return when {
            !afterBreak && isBreak -> FocusRun(b.title, left, true, nowMs, nowMs + left * 60_000L, blockId = b.id)
            afterBreak && b.kind.isStudy && autoFocus -> FocusRun(b.title, left, false, nowMs, nowMs + left * 60_000L, strict = strict, blockId = b.id)
            else -> null
        }
    }

    private fun distance(a: Int, b: Int): Int = minOf((a - b + DAY) % DAY, (b - a + DAY) % DAY)
}
