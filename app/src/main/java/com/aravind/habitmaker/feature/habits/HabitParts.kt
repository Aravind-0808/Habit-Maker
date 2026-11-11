package com.aravind.habitmaker.feature.habits

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.reminders.HabitReminders
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.StreakInfo
import com.aravind.habitmaker.core.util.Streaks
import com.aravind.habitmaker.data.db.Habit
import com.aravind.habitmaker.data.db.HabitLog
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.habitIcon
import com.aravind.habitmaker.ui.theme.colors
import com.aravind.habitmaker.widget.Widgets
import java.time.LocalDate

/** Writes for habits, shared by every habit page. */
object HabitActions {
    /** One tap adds one; tapping a finished habit clears it for that day. */
    suspend fun tap(context: Context, habit: Habit, day: Long = Dates.today()) {
        val dao = context.app.db.habitLogs()
        val now = dao.forDay(day).firstOrNull { it.habitId == habit.id }?.count ?: 0
        if (now >= habit.target) dao.delete(habit.id, day) else dao.upsert(HabitLog(habit.id, day, now + 1))
        Widgets.refresh(context)
    }

    suspend fun save(context: Context, habit: Habit): Long {
        val dao = context.app.db.habits()
        val id = if (habit.id == 0L) dao.upsert(habit) else { dao.upsert(habit); habit.id }
        if (context.app.prefs.current().remindersEnabled) HabitReminders.schedule(context, habit.copy(id = id))
        Widgets.refresh(context)
        return id
    }

    suspend fun delete(context: Context, habit: Habit) {
        HabitReminders.cancel(context, habit.id)
        context.app.db.habitLogs().deleteForHabit(habit.id)
        context.app.db.habits().delete(habit)
        Widgets.refresh(context)
    }
}

fun Habit.dueOn(day: LocalDate) = days and Dates.bit(day.dayOfWeek) != 0

fun Habit.streak(logs: List<HabitLog>, today: LocalDate = LocalDate.now()): StreakInfo {
    val done = logs.filter { it.habitId == id && it.count >= target }.map { it.day }.toSet()
    val first = minOf(Dates.dayOf(createdAt), done.minOrNull()?.let { LocalDate.ofEpochDay(it) } ?: today)
    return Streaks.compute(done, days, first, today)
}

/** A habit with its icon, a subtitle and a round check button. */
@Composable
fun HabitRow(habit: Habit, count: Int, subtitle: String, onCheck: () -> Unit, onOpen: () -> Unit, streak: Int = 0) {
    val c = MaterialTheme.colors
    val tint = Color(habit.color)
    val done = count >= habit.target
    AppCard(onClick = onOpen, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(habitIcon(habit.icon), tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleSmall, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (streak > 0) {
                        Icon(Icons.Filled.LocalFireDepartment, null, tint = c.warning, modifier = Modifier.size(14.dp))
                        Text("$streak  ", style = MaterialTheme.typography.bodySmall, color = c.warning)
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
                }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (done) tint else c.surface)
                    .border(2.dp, if (done) tint else c.borderStrong, CircleShape)
                    .clickable(onClick = onCheck),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    done -> Icon(Icons.Filled.Check, "Done", tint = Color.White)
                    habit.target > 1 -> Text("$count", style = MaterialTheme.typography.labelLarge, color = c.foreground)
                }
            }
        }
    }
}

/** Seven small dots: was the habit done on each of the last 7 days. */
@Composable
fun WeekDots(habit: Habit, logs: List<HabitLog>, today: LocalDate = LocalDate.now()) {
    val c = MaterialTheme.colors
    val done = logs.filter { it.habitId == habit.id && it.count >= habit.target }.map { it.day }.toSet()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).forEach { back ->
            val d = today.minusDays(back.toLong())
            val due = habit.dueOn(d)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Dates.SHORT_DAYS[d.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall, color = c.faint)
                Box(
                    Modifier.padding(top = 4.dp).size(14.dp).clip(CircleShape).background(
                        when {
                            d.toEpochDay() in done -> Color(habit.color)
                            due -> c.sunken
                            else -> Color.Transparent
                        },
                    ),
                )
            }
        }
    }
}
