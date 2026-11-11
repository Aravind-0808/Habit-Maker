package com.aravind.habitmaker.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.daysLabel
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.ConfirmDialog
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.LoadingState
import com.aravind.habitmaker.ui.components.Metric
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.habitIcon
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val WEEKS = 16

/** Streaks, a 16-week calendar and the last week (tap a day to fix it). */
@Composable
fun HabitDetailScreen(habitId: Long, onBack: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val habitState by remember { app.db.habits().observe(habitId) }.collectAsStateWithLifecycle(null)
    val logs by remember { app.db.habitLogs().observeForHabit(habitId) }.collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    val habit = habitState ?: run { ScrollScreen("Habit", onBack = onBack) { LoadingState() }; return }
    val tint = Color(habit.color)
    val counts = logs.associate { it.day to it.count }
    val info = habit.streak(logs, today)

    if (confirmDelete) ConfirmDialog(
        "Delete habit?", "\"${habit.name}\" and its history will be removed.", "Delete",
        onConfirm = { scope.launch { HabitActions.delete(context, habit); onBack() } }, onDismiss = { confirmDelete = false },
    )

    ScrollScreen(habit.name, onBack = onBack, actions = {
        IconButton({ go(Routes.habitEdit(habit.id)) }) { Icon(Icons.Filled.Edit, "Edit", tint = c.foreground) }
        IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = c.danger) }
    }) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(habitIcon(habit.icon), tint, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(habit.name, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    val reminder = habit.reminderMinute?.let { " · reminder ${formatClock(it)}" } ?: ""
                    Text(daysLabel(habit.days) + reminder, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Metric("${info.current}", "Current streak", Modifier.weight(1f), tint)
                Metric("${info.best}", "Best streak", Modifier.weight(1f))
                Metric("${info.totalDone}", "Days done", Modifier.weight(1f))
            }
        }

        Column {
            SectionLabel("Last 7 days")
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (6 downTo 0).forEach { back ->
                        val d = today.minusDays(back.toLong())
                        val n = counts[d.toEpochDay()] ?: 0
                        val done = n >= habit.target
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Dates.DAY_NAMES[d.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall, color = c.faint)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier.width(36.dp).aspectRatio(1f).clip(MaterialTheme.shapes.small)
                                    .background(if (done) tint else c.sunken)
                                    .clickable { scope.launch { HabitActions.tap(context, habit, d.toEpochDay()) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = if (done) Color.White else c.muted)
                            }
                        }
                    }
                }
            }
        }

        Column {
            SectionLabel("Last $WEEKS weeks")
            AppCard {
                // Columns are weeks (oldest left), rows Monday → Sunday.
                val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                val start = thisMonday.minusWeeks((WEEKS - 1).toLong())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(WEEKS) { w ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(7) { dow ->
                                val d = start.plusWeeks(w.toLong()).plusDays(dow.toLong())
                                val n = counts[d.toEpochDay()] ?: 0
                                val color = when {
                                    d.isAfter(today) -> Color.Transparent
                                    n >= habit.target -> tint
                                    n > 0 -> tint.copy(alpha = 0.4f)
                                    else -> c.sunken
                                }
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(color))
                            }
                        }
                    }
                }
            }
        }
    }
}
