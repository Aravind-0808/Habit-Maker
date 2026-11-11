package com.aravind.habitmaker.core.routine

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aravind.habitmaker.App
import com.aravind.habitmaker.MainActivity
import com.aravind.habitmaker.R
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.reminders.HabitReminders
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.data.db.RoutineLog
import com.aravind.habitmaker.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Alarms for the routine. Each block gets:
 *  - a START alarm (if [RoutineBlock.remind]): notification, the wake-up alarm, or auto-focus;
 *  - an END alarm for study blocks: "Did you finish?" with Done / Missed.
 * Every alarm re-schedules itself for the block's next day when it fires.
 */
object RoutineAlarms {
    const val ACTION_START = "com.aravind.habitmaker.routine.START"
    const val ACTION_END = "com.aravind.habitmaker.routine.END"
    const val ACTION_MARK = "com.aravind.habitmaker.routine.MARK"
    const val ACTION_RING = "com.aravind.habitmaker.routine.RING"
    const val EXTRA_ID = "block_id"
    const val EXTRA_STATUS = "status"
    const val EXTRA_DAY = "day"

    suspend fun scheduleAll(context: Context) {
        context.app.db.routine().blocks().forEach { schedule(context, it) }
    }

    fun schedule(context: Context, b: RoutineBlock) {
        cancel(context, b.id)
        if (b.days == 0) return
        val now = LocalDateTime.now()
        if (b.remind) {
            HabitReminders.nextTime(b.days, b.start, now)?.let { at(context, it, pending(context, ACTION_START, b.id), clock = b.kind == BlockType.WAKE) }
        }
        if (b.kind.isStudy) {
            HabitReminders.nextTime(b.days, b.end, now)?.let { at(context, it, pending(context, ACTION_END, b.id), clock = false) }
        }
    }

    fun cancel(context: Context, blockId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        listOf(ACTION_START, ACTION_END, ACTION_RING).forEach { am.cancel(pending(context, it, blockId)) }
    }

    /** Rings the alarm again in [minutes]. */
    fun snooze(context: Context, blockId: Long, minutes: Int) {
        val t = LocalDateTime.now().plusMinutes(minutes.toLong())
        at(context, t, pending(context, ACTION_RING, blockId), clock = true)
    }

    private fun at(context: Context, time: LocalDateTime, pi: PendingIntent, clock: Boolean) {
        val ms = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            if (clock) {
                val show = PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "prep"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(ms, show), pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun code(action: String, id: Long): Int = id.toInt() + when (action) {
        ACTION_START -> 100_000
        ACTION_END -> 200_000
        ACTION_RING -> 300_000
        else -> 400_000
    }

    private fun pending(context: Context, action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, code(action, id),
        Intent(context, RoutineReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Done / Missed buttons on the "Did you finish?" notification. */
    fun markIntent(context: Context, blockId: Long, day: Long, status: Int): PendingIntent = PendingIntent.getBroadcast(
        context, 500_000 + blockId.toInt() * 2 + status,
        Intent(context, RoutineReceiver::class.java).setAction(ACTION_MARK)
            .putExtra(EXTRA_ID, blockId).putExtra(EXTRA_DAY, day).putExtra(EXTRA_STATUS, status),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notificationId(blockId: Long) = 7000 + blockId.toInt()
}

class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(RoutineAlarms.EXTRA_ID, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = context.app.db.routine()
                when (intent.action) {
                    RoutineAlarms.ACTION_MARK -> {
                        val day = intent.getLongExtra(RoutineAlarms.EXTRA_DAY, Dates.today())
                        dao.upsertLog(RoutineLog(day, id, intent.getIntExtra(RoutineAlarms.EXTRA_STATUS, RoutineLog.DONE)))
                        NotificationManagerCompat.from(context).cancel(RoutineAlarms.notificationId(id))
                    }
                    RoutineAlarms.ACTION_RING -> {
                        val b = dao.block(id)
                        AlarmService.start(context, id, b?.title ?: "Wake up")
                    }
                    RoutineAlarms.ACTION_START -> {
                        val b = dao.block(id) ?: return@launch
                        RoutineAlarms.schedule(context, b)
                        if (b.kind == BlockType.WAKE) {
                            AlarmService.start(context, b.id, b.title)
                        } else {
                            notify(context, b, "${formatClock(b.start)} · ${b.title}", startText(b))
                            val plan = dao.plan()
                            // Keep the focus timer in step with the routine: study → focus until the block ends,
                            // break → break timer until the break ends. A focus the user started themselves is left alone.
                            val run = FocusTimer.run.value
                            val free = run == null || run.blockId != b.id && (run.isBreak || run.blockId != null)
                            if (plan?.autoFocus == true && free) {
                                when {
                                    b.kind.isStudy -> FocusTimer.start(context, b.minutes(), b.title, strict = plan.strictFocus, blockId = b.id)
                                    b.kind == BlockType.BREAK || b.kind == BlockType.MEAL ->
                                        if (run == null || run.isBreak) FocusTimer.start(context, b.minutes(), b.title, isBreak = true, blockId = b.id)
                                        else Unit
                                }
                            }
                        }
                    }
                    RoutineAlarms.ACTION_END -> {
                        val b = dao.block(id) ?: return@launch
                        RoutineAlarms.schedule(context, b)
                        // Study is done only when its focus timer ran to the end (FocusTimer logs DONE).
                        // Give a running timer a moment to finish, then anything else is missed.
                        kotlinx.coroutines.delay(5_000)
                        val today = Dates.today()
                        val done = dao.logs().any { it.day == today && it.blockId == b.id && it.status == RoutineLog.DONE }
                        if (!done) {
                            dao.upsertLog(RoutineLog(today, b.id, RoutineLog.MISSED))
                            notify(context, b, "\"${b.title}\" not done", "Its focus session wasn't finished, so it's marked as missed.")
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun startText(b: RoutineBlock) = when (b.kind) {
        BlockType.BREAK, BlockType.MEAL -> "Take ${formatMinutes(b.minutes())} — stretch, drink water, rest your eyes."
        BlockType.REFRESH -> "Freshen up and get ready for the day."
        BlockType.SLEEP -> "Time to sleep. A good night helps you remember what you studied."
        else -> if (b.kind.isStudy) "${formatMinutes(b.minutes())} of focused study. Phone down!" else "Until ${formatClock(b.end)}"
    }

    private fun notify(context: Context, b: RoutineBlock, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "prep"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, App.CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(RoutineAlarms.notificationId(b.id), n.build())
    }
}
