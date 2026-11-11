package com.aravind.habitmaker.feature.habits

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
    val habits by remember { app.db.habits().observeActive() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember { app.db.habitLogs().observeSince(0) }.collectAsStateWithLifecycle(emptyList())
    var onlyToday by remember { mutableStateOf(false) }

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
        AppCard {
            Row {
                Metric("${habits.size}", "Habits", Modifier.weight(1f))
                Metric("${streaks.values.maxOfOrNull { it.current } ?: 0}", "Best current streak", Modifier.weight(1f))
                Metric("${streaks.values.sumOf { it.totalDone }}", "Times done", Modifier.weight(1f))
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
