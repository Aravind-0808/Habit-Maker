package com.aravind.habitmaker

import com.aravind.habitmaker.core.focus.FocusRun
import com.aravind.habitmaker.core.focus.acceptsNotificationAction
import com.aravind.habitmaker.core.focus.notificationState
import com.aravind.habitmaker.core.reminders.checkedInCount
import org.junit.Assert.*
import org.junit.Test

class NotificationControlsTest {
    private val run = FocusRun("Read", 10, false, 1000, 601000)

    @Test fun progressReflectsElapsedTime() {
        val state = run.notificationState(301000)
        assertEquals(50, state.percent)
        assertEquals(300000L, state.remainingMs)
    }
    @Test fun pausedCountdownDoesNotAdvance() {
        val paused = run.copy(pausedRemaining = 300000L)
        assertEquals(paused.notificationState(301000), paused.notificationState(901000))
    }
    @Test fun progressStaysInBounds() {
        assertEquals(0, run.notificationState(0).percent)
        assertEquals(100, run.notificationState(999999).percent)
        assertEquals(100, run.copy(plannedMin = 0).notificationState(1000).percent)
    }
    @Test fun staleActionsCannotControlNewRun() {
        assertFalse(run.acceptsNotificationAction(999, "stop"))
        assertTrue(run.acceptsNotificationAction(1000, "stop"))
    }
    @Test fun strictActionsAreRejected() {
        val strict = run.copy(strict = true)
        listOf("pause", "resume", "stop").forEach { assertFalse(strict.acceptsNotificationAction(1000, it)) }
        assertFalse(strict.notificationState(1000).canPause)
        assertFalse(strict.notificationState(1000).canStop)
    }
    @Test fun scheduledTimersCannotBePausedPastTheirSlot() {
        val scheduled = run.copy(blockId = 1)
        assertFalse(scheduled.acceptsNotificationAction(1000, "pause"))
        assertFalse(scheduled.notificationState(1000).canPause)
    }
    @Test fun pauseAndResumeOnlyApplyToMatchingStates() {
        assertTrue(run.acceptsNotificationAction(1000, "pause"))
        assertFalse(run.acceptsNotificationAction(1000, "resume"))
        assertTrue(run.copy(pausedRemaining = 100).acceptsNotificationAction(1000, "resume"))
    }
    @Test fun checkInsAreIdempotentAndNeverUndoCompletion() {
        assertEquals(1, checkedInCount(0, 1, 0))
        assertEquals(1, checkedInCount(1, 1, 0))
        assertEquals(1, checkedInCount(1, 3, 0))
        assertEquals(2, checkedInCount(1, 3, 1))
        assertEquals(3, checkedInCount(3, 3, 3))
    }
}
