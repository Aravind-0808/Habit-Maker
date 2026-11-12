package com.aravind.habitmaker.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.net.toUri
import com.aravind.habitmaker.core.notifications.notificationAccent
import com.aravind.habitmaker.core.focus.notificationState
import com.aravind.habitmaker.core.focus.acceptsNotificationAction
import com.aravind.habitmaker.core.util.formatClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aravind.habitmaker.App
import com.aravind.habitmaker.MainActivity
import com.aravind.habitmaker.R
import com.aravind.habitmaker.core.focus.FocusRun
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.routine.RoutineFlow
import com.aravind.habitmaker.core.util.formatTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps the focus timer running with the app closed, with a countdown notification. */
class FocusTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var accentColor: Int = android.graphics.Color.GRAY

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // checked by canNotify()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = FocusTimer.run.value
        val action = intent?.action
        if (action in setOf("pause", "resume", "stop")) {
            if (current == null) { stopSelf(startId); return START_NOT_STICKY }
            if (!current.acceptsNotificationAction(intent?.getLongExtra("run_token", -1L) ?: -1L, action!!)) return START_NOT_STICKY
            when (action) {
                "pause" -> FocusTimer.pause()
                "resume" -> FocusTimer.resume()
                "stop" -> { FocusTimer.stop(this); return START_NOT_STICKY }
            }
        }
        val run = FocusTimer.run.value
        if (run == null) { stopSelf(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(run), type)
        loop?.cancel()
        loop = scope.launch {
            accentColor = notificationAccent(this@FocusTimerService)
            var lastShown = ""
            while (isActive) {
                val r = FocusTimer.run.value ?: break
                if (!r.paused && r.remainingMs() <= 0) {
                    // In the routine, go straight on: study → break → next study, without stopping the service.
                    val next = RoutineFlow.nextRun(this@FocusTimerService, r)
                    if (FocusTimer.run.value != r) continue
                    FocusTimer.finish(this@FocusTimerService, completed = true, stopService = next == null)
                    notifyDone(r, next)
                    if (next == null) break
                    FocusTimer.continueWith(next)
                    lastShown = ""
                    continue
                }
                val key = "${r.startedAt}-${r.paused}-${r.remainingMs() / 15_000}"
                if (key != lastShown) {
                    lastShown = key
                    if (canNotify()) NotificationManagerCompat.from(this@FocusTimerService).notify(NOTIF_ID, notification(r))
                }
                delay(1000)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "focus"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun timerAction(r: FocusRun, action: String): PendingIntent = PendingIntent.getService(
        this, 0,
        Intent(this, FocusTimerService::class.java).setAction(action)
            .setData("habitmaker://focus/${r.startedAt}/$action".toUri()).putExtra("run_token", r.startedAt),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notification(r: FocusRun): Notification {
        val state = r.notificationState(System.currentTimeMillis())
        val kind = if (r.isBreak) "Break" else "Focus"
        val detail = if (r.paused) "Paused · ${formatTimer(state.remainingMs / 1000)} remaining"
            else "$kind in progress · ${r.plannedMin} min session"
        val b = NotificationCompat.Builder(this, App.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setColor(accentColor)
            .setContentTitle(if (r.isBreak) "Recharge time" else r.label)
            .setContentText(detail)
            .setSubText(if (r.strict) "Strict focus · controls locked" else if (r.blockId != null) "Exam Prep · follows your timetable" else "$kind timer")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail + if (r.strict) "\nThis session stays locked until the timer finishes." else ""))
            .setProgress(100, state.percent, false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(openApp())
        if (!r.paused) b.setWhen(r.endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        else b.setShowWhen(false).setUsesChronometer(false)
        if (state.canPause) b.addAction(0, if (r.paused) "Resume" else "Pause", timerAction(r, if (r.paused) "resume" else "pause"))
        if (state.canStop) b.addAction(0, if (r.isBreak) "End break" else "End focus", timerAction(r, "stop"))
        b.addAction(0, "Open timer", openApp())
        return b.build()
    }

    @SuppressLint("MissingPermission") // checked by canNotify()
    private fun notifyDone(r: FocusRun, next: FocusRun?) {
        if (!canNotify()) return
        val text = when {
            next != null && next.isBreak -> "✓ Marked done. Take a ${next.plannedMin} min break — stand up, drink water, rest your eyes."
            next != null -> "Break over — \"${next.label}\" has started. Phone down!"
            r.isBreak -> "Ready for another round?"
            r.blockId != null -> "✓ ${r.label} marked done — ${r.plannedMin} minutes of focus."
            else -> "Great work — ${r.plannedMin} minutes of focus."
        }
        val n = NotificationCompat.Builder(this, App.CHANNEL_FOCUS_DONE)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setColor(accentColor)
            .setSubText(if (r.isBreak) "Break finished" else "${r.plannedMin} min focused")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .addAction(0, if (next != null) "View current timer" else "View session", openApp())
            .setContentTitle(if (r.isBreak) "Break is over" else if (next?.isBreak == true) "Session done — break time" else "Focus session complete")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(DONE_ID, n)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val NOTIF_ID = 42
        const val DONE_ID = 43
    }
}
