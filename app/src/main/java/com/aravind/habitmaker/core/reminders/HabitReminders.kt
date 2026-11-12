package com.aravind.habitmaker.core.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import com.aravind.habitmaker.core.notifications.notificationAccent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aravind.habitmaker.App
import com.aravind.habitmaker.MainActivity
import com.aravind.habitmaker.R
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.routine.RoutineAlarms
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.data.db.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** One alarm per habit, always for its next scheduled day. */
object HabitReminders {
    fun schedule(context: Context, habit: Habit) {
        cancel(context, habit.id)
        val next = nextTime(habit, LocalDateTime.now()) ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, habit.id)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, habitId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, habitId))
    }

    suspend fun rescheduleAll(context: Context) {
        val enabled = context.app.prefs.current().remindersEnabled
        context.app.db.habits().all().forEach { if (enabled) schedule(context, it) else cancel(context, it.id) }
    }

    /** One pending alarm per habit; repeat habits advance to the next slot in their window. */
    fun nextTime(habit: Habit, now: LocalDateTime): LocalDateTime? {
        val minute = habit.reminderMinute ?: return null
        if (habit.archived || habit.days == 0 || minute !in 0..1439) return null
        if (habit.timing != "REPEAT") return nextTime(habit.days, minute, now)
        val end = habit.endMinute ?: return null
        val interval = habit.intervalMin ?: return null
        if (end !in minute..1439 || interval <= 0) return null
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (habit.days and Dates.bit(date.dayOfWeek) == 0) continue
            for (slot in minute..end step interval) {
                val at = date.atTime(slot / 60, slot % 60)
                if (at.isAfter(now)) return at
            }
        }
        return null
    }

    /** The next time at [minute] on one of [days] strictly after [now]. */
    fun nextTime(days: Int, minute: Int, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        for (i in 0..7) {
            val d = today.plusDays(i.toLong())
            val t = d.atTime(minute / 60, minute % 60)
            if (days and Dates.bit(d.dayOfWeek) != 0 && t.isAfter(now)) return t
        }
        return null
    }

    private fun pending(context: Context, habitId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, habitId.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    const val EXTRA_ID = "habit_id"
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(HabitReminders.EXTRA_ID, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.app
                val habit = app.db.habits().get(id) ?: return@launch
                if (!app.prefs.current().remindersEnabled || habit.archived || habit.reminderMinute == null) return@launch
                if (habit.days and Dates.bit(java.time.LocalDate.now().dayOfWeek) == 0) {
                    HabitReminders.schedule(context, habit)
                    return@launch
                }
                val done = app.db.habitLogs().forDay(Dates.today()).firstOrNull { it.habitId == id }?.count ?: 0
                if (done < habit.target) showHabitReminder(context, habit, done)
                HabitReminders.schedule(context, habit)
            } finally {
                pending.finish()
            }
        }
    }

}

internal suspend fun showHabitReminder(context: Context, habit: Habit, count: Int, silent: Boolean = false) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val day = Dates.today()
    val checkIn = PendingIntent.getBroadcast(context, 0,
        Intent(context, HabitNotificationActionReceiver::class.java)
            .setData("habitmaker://habit/${habit.id}/$day/$count".toUri())
            .putExtra("habit_id", habit.id).putExtra("day", day).putExtra("expected_count", count),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val detail = when {
        habit.timing == "WINDOW" && habit.endMinute != null -> "${formatClock(habit.startMinute ?: habit.reminderMinute ?: 0)} – ${formatClock(habit.endMinute)} · Your planned time"
        habit.timing == "REPEAT" -> "$count of ${habit.target} check-ins today · Every ${habit.intervalMin} min"
        else -> "A small step for today. Check it off when you're done."
    }
    val n = NotificationCompat.Builder(context, App.CHANNEL_REMINDERS)
        .setSmallIcon(R.drawable.ic_stat_habit).setColor(notificationAccent(context))
        .setContentTitle(habit.name).setContentText(detail)
        .setSubText(if (habit.target > 1) "Daily habit · $count/${habit.target}" else "Habit reminder")
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
        .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(silent).setSilent(silent)
        .addAction(0, if (habit.target > 1) "Log a check-in" else "Mark done", checkIn)
        .addAction(0, "Open app", open)
    if (habit.target > 1) n.setProgress(habit.target, count.coerceIn(0, habit.target), false)
    NotificationManagerCompat.from(context).notify(1000 + habit.id.toInt(), n.build())
}

/** Alarms are cleared on reboot and app update; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HabitReminders.rescheduleAll(context)
                RoutineAlarms.scheduleAll(context)
            } finally { pending.finish() }
        }
    }
}
