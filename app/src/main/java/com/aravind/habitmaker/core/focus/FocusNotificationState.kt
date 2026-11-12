package com.aravind.habitmaker.core.focus

internal data class FocusNotificationState(val remainingMs: Long, val percent: Int, val canPause: Boolean, val canStop: Boolean)

internal fun FocusRun.notificationState(now: Long): FocusNotificationState {
    val left = remainingMs(now).coerceIn(0L, totalMs.coerceAtLeast(0L))
    val percent = if (totalMs <= 0L) 100 else ((totalMs - left) * 100 / totalMs).toInt().coerceIn(0, 100)
    return FocusNotificationState(left, percent, !strict && blockId == null, !strict)
}

/** Actions belong to one exact run, never to whichever timer happens to be active later. */
internal fun FocusRun.acceptsNotificationAction(token: Long, action: String): Boolean {
    if (token != startedAt || strict) return false
    return when (action) {
        "pause" -> blockId == null && !paused
        "resume" -> blockId == null && paused
        "stop" -> true
        else -> false
    }
}
