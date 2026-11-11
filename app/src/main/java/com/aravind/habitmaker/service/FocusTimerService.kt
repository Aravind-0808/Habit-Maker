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

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // checked by canNotify()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val run = FocusTimer.run.value
        if (run == null) { stopSelf(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(run), type)
        loop?.cancel()
        loop = scope.launch {
            var lastShown = ""
            while (isActive) {
                val r = FocusTimer.run.value ?: break
                if (!r.paused && r.remainingMs() <= 0) {
                    // In the routine, go straight on: study → break → next study, without stopping the service.
                    val next = RoutineFlow.nextRun(this@FocusTimerService, r)
                    FocusTimer.finish(this@FocusTimerService, completed = true, stopService = next == null)
                    notifyDone(r, next)
                    if (next == null) break
                    FocusTimer.continueWith(next)
                    lastShown = ""
                    continue
                }
                val key = "${r.paused}-${r.remainingMs() / 60_000}"
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

    private fun notification(r: FocusRun): Notification {
        val b = NotificationCompat.Builder(this, App.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(if (r.isBreak) "Break" else r.label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
        if (r.paused) {
            b.setContentText("Paused · ${formatTimer(r.remainingMs() / 1000)} left")
        } else {
            b.setContentText(if (r.isBreak) "Relax, you earned it" else "Stay focused")
                .setWhen(r.endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
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
