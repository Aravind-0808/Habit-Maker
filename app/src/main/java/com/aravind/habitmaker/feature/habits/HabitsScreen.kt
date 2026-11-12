package com.aravind.habitmaker.feature.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.ui.components.SkeletonScreen
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.daysLabel
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.EmptyState
import com.aravind.habitmaker.ui.components.Metric
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.Segmented
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch
import java.time.LocalDate

/** All habits with streaks and the last 7 days. */
@Composable
fun HabitsScreen(go: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val loadedHabits by remember { app.db.habits().observeActive() }.collectAsStateWithLifecycle(initialValue = null)
    val loadedLogs by remember { app.db.habitLogs().observeSince(0) }.collectAsStateWithLifecycle(initialValue = null)
    var onlyToday by remember { mutableStateOf(false) }

    val habits = loadedHabits ?: run { SkeletonScreen("Habits", onBack); return }
    val logs = loadedLogs ?: run { SkeletonScreen("Habits", onBack); return }

    val shown = if (onlyToday) habits.filter { it.dueOn(today) } else habits
    val todayLogs = logs.filter { it.day == today.toEpochDay() }.associateBy { it.habitId }
    val c = MaterialTheme.colors

    ScrollScreen("Habits", onBack = onBack, actions = {
        IconButton(onClick = { go(Routes.habitEdit()) }) { Icon(Icons.Filled.Add, "Add habit", tint = c.foreground) }
    }) {
        if (habits.isEmpty()) {
            AppCard {
                EmptyState(Icons.Filled.CheckCircle, "Start your first habit", "Small daily actions add up. Pick one to begin.") {
                    AppButton("Add habit", { go(Routes.habitEdit()) }, fullWidth = false, icon = Icons.Filled.Add)
                }
            }
            return@ScrollScreen
        }

        val streaks = habits.associate { it.id to it.streak(logs, today) }
        val dueToday = habits.filter { it.dueOn(today) }
        val doneToday = dueToday.count { (todayLogs[it.id]?.count ?: 0) >= it.target }

        // 7-Day Micro Ring Strip Card (from habits_tracker redesign)
        AppCard(elevated = true, padding = 16.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarToday, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Weekly Rhythm", style = MaterialTheme.typography.titleMedium, color = c.foreground, fontWeight = FontWeight.Bold)
                }
                Text("$doneToday/${dueToday.size} completed today", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (6 downTo 0).forEach { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    val isToday = daysAgo == 0
                    val dueOnDate = habits.filter { it.dueOn(date) }
                    val doneOnDate = dueOnDate.count { h -> logs.any { it.habitId == h.id && it.day == date.toEpochDay() && it.count >= h.target } }
                    val fraction = if (dueOnDate.isNotEmpty()) doneOnDate.toFloat() / dueOnDate.size else 0f
                    val dayLetter = date.dayOfWeek.name.take(1)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = if (isToday) Modifier.clip(RoundedCornerShape(8.dp)).background(c.surfaceElevated).padding(horizontal = 4.dp, vertical = 2.dp) else Modifier,
                    ) {
                        Text(dayLetter, style = MaterialTheme.typography.labelSmall, color = if (isToday) c.primary else c.muted, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stroke = 3.dp.toPx()
                                drawCircle(c.border.copy(alpha = 0.3f), style = Stroke(stroke))
                                drawArc(
                                    color = if (isToday) c.primary else c.primary.copy(alpha = 0.8f),
                                    startAngle = -90f,
                                    sweepAngle = 360f * fraction,
                                    useCenter = false,
                                    style = Stroke(stroke),
                                )
                            }
                            if (fraction >= 0.99f) {
                                Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(16.dp))
                            } else {
                                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = if (isToday) c.primary else c.muted)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(if (isToday) "Today" else "${doneOnDate}/${dueOnDate.size}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = if (isToday) c.primary else c.muted)
                    }
                }
            }
        }

        Segmented(listOf(false to "All", true to "Due today"), onlyToday, { onlyToday = it })

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { h ->
                val count = todayLogs[h.id]?.count ?: 0
                Column {
                    HabitRow(
                        h, count,
                        subtitle = daysLabel(h.days) + if (h.target > 1) " · ${h.target}× a day" else "",
                        onCheck = { scope.launch { HabitActions.tap(context, h) } },
                        onOpen = { go(Routes.habitDetail(h.id)) },
                        streak = streaks[h.id]?.current ?: 0,
                    )
                    WeekDots(h, logs, today)
                }
            }
        }
    }
}
