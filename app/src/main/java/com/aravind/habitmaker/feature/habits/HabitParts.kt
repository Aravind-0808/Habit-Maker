package com.aravind.habitmaker.feature.habits

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.reminders.HabitReminders
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.StreakInfo
import com.aravind.habitmaker.core.util.Streaks
import com.aravind.habitmaker.data.db.Habit
import com.aravind.habitmaker.data.db.HabitLog
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.GeometricSparkBurst
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.ProgressBar
import com.aravind.habitmaker.ui.components.bouncyClickable
import com.aravind.habitmaker.ui.components.habitIcon
import com.aravind.habitmaker.ui.components.rememberBreathingGlow
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

fun Habit.timingLabel(): String {
    val start = startMinute ?: reminderMinute
    return when {
        timing == "REPEAT" && start != null && endMinute != null -> "$target times · ${formatClock(start)}–${formatClock(endMinute)}"
        timing == "WINDOW" && start != null && endMinute != null -> "${formatClock(start)}–${formatClock(endMinute)}"
        start != null -> formatClock(start)
        target > 1 -> "$target times per day · Anytime"
        else -> "Anytime"
    }
}

fun Habit.streak(logs: List<HabitLog>, today: LocalDate = LocalDate.now()): StreakInfo {
    val done = logs.filter { it.habitId == id && it.count >= target }.map { it.day }.toSet()
    val first = minOf(Dates.dayOf(createdAt), done.minOrNull()?.let { LocalDate.ofEpochDay(it) } ?: today)
    return Streaks.compute(done, days, first, today)
}

/** A habit with animated spring micro-interactions, haptics, and particle completion effects. */
@Composable
fun HabitRow(habit: Habit, count: Int, subtitle: String, onCheck: () -> Unit, onOpen: () -> Unit, streak: Int = 0) {
    val c = MaterialTheme.colors
    val tint = Color(habit.color)
    val done = count >= habit.target
    val haptic = LocalHapticFeedback.current

    var checkTrigger by remember { mutableIntStateOf(0) }
    val flameAlpha = rememberBreathingGlow(minAlpha = 0.65f, maxAlpha = 1f, durationMs = 1800)

    val checkScale by animateFloatAsState(
        targetValue = if (done) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "check_scale",
    )

    AppCard(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(habitIcon(habit.icon), tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (done) c.muted else c.foreground,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(habit.timingLabel(), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (streak > 0) {
                        Box(
                            Modifier
                                .graphicsLayer { alpha = flameAlpha }
                                .clip(RoundedCornerShape(50))
                                .background(c.warningSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = c.warning, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("$streak d", style = MaterialTheme.typography.labelSmall, color = c.warning, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (done) c.success else c.muted,
                        maxLines = 1,
                    )
                }
            }

            Box(contentAlignment = Alignment.Center) {
                // Spark Burst Effect on Completion
                GeometricSparkBurst(
                    triggerKey = if (checkTrigger > 0 && done) checkTrigger else null,
                    color = tint,
                    modifier = Modifier.size(44.dp),
                )

                Box(
                    Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                        .clip(CircleShape)
                        .background(if (done) tint else c.surface)
                        .border(1.5.dp, if (done) tint else c.borderStrong, CircleShape)
                        .bouncyClickable(scaleDown = 0.88f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            checkTrigger++
                            onCheck()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = done,
                        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
                        exit = scaleOut(),
                    ) {
                        Icon(Icons.Filled.Check, "Done", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    if (!done && habit.target > 1) {
                        Text("$count", style = MaterialTheme.typography.labelMedium, color = c.foreground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (habit.target > 1) {
            Spacer(Modifier.height(8.dp))
            ProgressBar(
                fraction = count.toFloat() / habit.target,
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = tint,
                trackColor = c.sunken,
            )
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
