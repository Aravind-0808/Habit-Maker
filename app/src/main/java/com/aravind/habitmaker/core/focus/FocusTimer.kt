package com.aravind.habitmaker.core.focus

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.routine.contains
import com.aravind.habitmaker.core.util.Dates
import java.time.Instant
import java.time.ZoneId
import com.aravind.habitmaker.data.db.FocusSession
import com.aravind.habitmaker.data.db.RoutineLog
import com.aravind.habitmaker.service.FocusTimerService
import com.aravind.habitmaker.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FocusRun(
    val label: String,
    val plannedMin: Int,
    val isBreak: Boolean,
    val startedAt: Long,
    /** When it ends (while running). */
    val endsAt: Long,
    /** Remaining ms while paused, else null. */
    val pausedRemaining: Long? = null,
    /** Cannot be stopped early. */
    val strict: Boolean = false,
    /** The Exam Prep block this run belongs to (its timer ends with the block). */
    val blockId: Long? = null,
) {
    val paused get() = pausedRemaining != null
    fun remainingMs(now: Long = System.currentTimeMillis()) = pausedRemaining ?: (endsAt - now).coerceAtLeast(0)
    val totalMs get() = plannedMin * 60_000L
}

/** A finished run, shown once by the focus page. */
data class FocusResult(val minutes: Int, val completed: Boolean, val wasBreak: Boolean, val blockId: Long? = null)

/** The one focus timer of the app. The service keeps it alive and shows its notification. */
object FocusTimer {
    private val _run = MutableStateFlow<FocusRun?>(null)
    val run: StateFlow<FocusRun?> = _run.asStateFlow()

    private val _result = MutableStateFlow<FocusResult?>(null)
    val result: StateFlow<FocusResult?> = _result.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val running get() = _run.value?.let { !it.isBreak } == true

    fun start(context: Context, minutes: Int, label: String, isBreak: Boolean = false, strict: Boolean = false, blockId: Long? = null) {
        val now = System.currentTimeMillis()
        _run.value = FocusRun(label.ifBlank { if (isBreak) "Break" else "Focus" }, minutes, isBreak, now, now + minutes * 60_000L, strict = strict && !isBreak, blockId = blockId)
        _result.value = null
        ContextCompat.startForegroundService(context, Intent(context, FocusTimerService::class.java))
    }

    fun pause() {
        val r = _run.value ?: return
        if (r.paused) return
        _run.value = r.copy(pausedRemaining = r.remainingMs())
    }

    fun resume() {
        val r = _run.value ?: return
        val left = r.pausedRemaining ?: return
        _run.value = r.copy(endsAt = System.currentTimeMillis() + left, pausedRemaining = null)
    }

    /** Stops early (ignored for a strict run). */
    fun stop(context: Context) {
        val r = _run.value ?: return
        if (r.strict) return
        finish(context, completed = false)
    }

    /** Called by the service when time is up, or by [stop]. */
    /** The routine's next timer (break after study…), set by the service without restarting it. */
    fun continueWith(next: FocusRun) {
        _run.value = next
        _result.value = null
    }

    fun finish(context: Context, completed: Boolean, stopService: Boolean = true) {
        val r = _run.value ?: return
        _run.value = null
        val now = System.currentTimeMillis()
        val doneMin = if (completed) r.plannedMin else ((r.totalMs - r.remainingMs(now)) / 60_000).toInt()
        _result.value = FocusResult(doneMin, completed, r.isBreak, r.blockId)
        if (stopService) context.stopService(Intent(context, FocusTimerService::class.java))
        if (!r.isBreak && doneMin > 0) {
            val app = context.app
            scope.launch {
                app.db.focus().insert(FocusSession(start = r.startedAt, end = now, plannedMin = r.plannedMin, actualMin = doneMin, completed = completed, label = r.label))
                // A routine study session counts as done only if its focus timer ran to the end.
                if (completed && r.blockId != null) {
                    val block = app.db.routine().block(r.blockId)
                    val started = Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).toLocalTime()
                    val m = started.hour * 60 + started.minute
                    // Inside its time slot (or up to 3 min early, when it follows a break automatically).
                    if (block != null && (block.contains(m) || (block.start - m + 24 * 60) % (24 * 60) <= 3)) {
                        app.db.routine().upsertLog(RoutineLog(Dates.today(), r.blockId, RoutineLog.DONE))
                    }
                }
                Widgets.refresh(app)
            }
        }
    }

    fun clearResult() { _result.value = null }
}
