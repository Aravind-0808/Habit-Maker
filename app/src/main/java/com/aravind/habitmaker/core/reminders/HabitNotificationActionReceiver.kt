package com.aravind.habitmaker.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.data.db.HabitLog
import com.aravind.habitmaker.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

internal fun checkedInCount(current: Int, target: Int, expected: Int): Int =
    if (current == expected && current < target && current >= 0) current + 1 else current

/** A check-in is idempotent: double taps never undo or duplicate a completed habit. */
class HabitNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("habit_id", -1)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.app
                val day = intent.getLongExtra("day", -1)
                if (day != Dates.today()) {
                    NotificationManagerCompat.from(context).cancel(1000 + id.toInt())
                    return@launch
                }
                val habit = app.db.habits().get(id) ?: return@launch
                if (habit.archived || habit.days and Dates.bit(LocalDate.now().dayOfWeek) == 0) return@launch
                val count = app.db.withTransaction {
                    val dao = app.db.habitLogs()
                    val current = dao.forDay(day).firstOrNull { it.habitId == id }?.count ?: 0
                    val next = checkedInCount(current, habit.target, intent.getIntExtra("expected_count", -1))
                    if (next != current) dao.upsert(HabitLog(id, day, next))
                    next
                }
                if (count >= habit.target) NotificationManagerCompat.from(context).cancel(1000 + id.toInt())
                else showHabitReminder(context, habit, count, silent = true)
                if (app.prefs.current().remindersEnabled) HabitReminders.schedule(context, habit)
                Widgets.refresh(context)
            } finally { pending.finish() }
        }
    }
}
