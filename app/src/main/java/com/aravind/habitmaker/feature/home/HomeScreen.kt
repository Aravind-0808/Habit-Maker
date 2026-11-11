package com.aravind.habitmaker.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.auth.AuthState
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.core.util.formatTimer
import com.aravind.habitmaker.data.usage.DayUsage
import com.aravind.habitmaker.feature.habits.HabitActions
import com.aravind.habitmaker.feature.habits.HabitRow
import com.aravind.habitmaker.feature.habits.dueOn
import com.aravind.habitmaker.feature.habits.streak
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.Avatar
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.EmptyState
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.ProgressBar
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.Blue
import com.aravind.habitmaker.ui.theme.Emerald
import com.aravind.habitmaker.ui.theme.Violet
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.TextButton
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.feature.prep.current
import com.aravind.habitmaker.feature.prep.icon
import com.aravind.habitmaker.feature.prep.next
import com.aravind.habitmaker.feature.prep.on
import com.aravind.habitmaker.feature.prep.tint

/** Today at a glance: screen time, focus, unlocks and today's habits. */
@Composable
fun HomeScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    val user by app.auth.state.collectAsStateWithLifecycle(AuthState())
    val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
    val habits by remember { app.db.habits().observeActive() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember { app.db.habitLogs().observeSince(0) }.collectAsStateWithLifecycle(emptyList())
    val focus by remember { app.db.focus().observeSince(Dates.startOfToday()) }.collectAsStateWithLifecycle(emptyList())
    val run by FocusTimer.run.collectAsStateWithLifecycle()
    val plan by remember { app.db.routine().observePlan() }.collectAsStateWithLifecycle(null)
    val routine by remember { app.db.routine().observeBlocks() }.collectAsStateWithLifecycle(emptyList())

    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose {} }
    val usage by produceState<DayUsage?>(null, resumes) { value = app.usage.today() }
    val hasAccess = remember(resumes) { app.usage.hasAccess() }

    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }

    val due = habits.filter { it.dueOn(today) }
    val todayLogs = logs.filter { it.day == today.toEpochDay() }.associateBy { it.habitId }
    val doneCount = due.count { (todayLogs[it.id]?.count ?: 0) >= it.target }
    val focusMin = focus.sumOf { it.actualMin }

    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    ScrollScreen("Habit Maker", actions = { Avatar(user.initials, 36.dp) { go(Routes.PROFILE) }; Spacer(Modifier.width(12.dp)) }) {
        Text(
            if (user.name.isNotBlank()) "$greeting, ${user.name.substringBefore(' ')}" else greeting,
            style = MaterialTheme.typography.headlineSmall, color = c.foreground,
        )

        // Hero: screen time against the daily target
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))).padding(20.dp),
        ) {
            Text("Screen time today", style = MaterialTheme.typography.labelMedium, color = c.onHero.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            if (hasAccess) {
                val used = usage?.totalMs ?: 0L
                Text(formatDuration(used), style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                Spacer(Modifier.height(12.dp))
                ProgressBar(used / (s.dailyTargetMin * 60_000f), color = if (used > s.dailyTargetMin * 60_000L) c.danger else c.onHero, trackColor = c.onHero.copy(alpha = 0.2f))
                Spacer(Modifier.height(6.dp))
                Text("Target ${formatMinutes(s.dailyTargetMin)}", style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.7f))
            } else {
                Text("—", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                Spacer(Modifier.height(8.dp))
                Text("Allow usage access to see your screen time.", style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.7f))
                Spacer(Modifier.height(12.dp))
                AppButton("Allow", { go(Routes.PERMISSIONS) }, kind = ButtonKind.SECONDARY, fullWidth = false)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Focus", formatMinutes(focusMin), Icons.Filled.Timer, Violet, Modifier.weight(1f))
            StatTile("Unlocks", usage?.unlocks?.takeIf { it >= 0 }?.toString() ?: "—", Icons.Filled.LockOpen, Blue, Modifier.weight(1f))
            StatTile("Habits", "$doneCount/${due.size}", Icons.Filled.CheckCircle, Emerald, Modifier.weight(1f))
        }

        if (s.blockNowUntil > now) {
            AppCard(onClick = { go(Routes.BLOCK) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Shield, c.danger)
                    Spacer(Modifier.width(12.dp))
                    Text("Blocking distracting apps", style = MaterialTheme.typography.titleSmall, color = c.foreground, modifier = Modifier.weight(1f))
                    Badge(formatTimer((s.blockNowUntil - now) / 1000), Tone.DANGER)
                }
            }
        }

        // Exam prep: what's on now
        val p = plan
        if (p != null) {
            val todays = routine.on(today)
            val minute = LocalTime.now().let { it.hour * 60 + it.minute }
            val cur = todays.current(minute)
            val nxt = todays.next(minute)
            val days = p.examDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }
            AppCard(onClick = { go(Routes.PREP) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(cur?.kind?.icon() ?: Icons.Filled.School, cur?.kind?.tint() ?: c.foreground)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cur?.let { "Now: ${it.title}" } ?: "Exam Prep", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text(nxt?.let { "Next: ${formatClock(it.start)} · ${it.title}" } ?: "Nothing more today", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    if (days != null && days >= 0) Badge("${p.exam} · ${days}d", Tone.INFO)
                }
            }
        }

        val r = run
        if (r != null) {
            AppCard(onClick = { go(Routes.FOCUS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Timer, Violet)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text(if (r.paused) "Paused" else "In progress", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Text(formatTimer(r.remainingMs(now) / 1000), style = MaterialTheme.typography.titleLarge, color = c.foreground)
                }
            }
        } else {
            AppButton("Start ${s.defaultFocusMin} min focus", { FocusTimer.start(context, s.defaultFocusMin, "Focus"); go(Routes.FOCUS) }, icon = Icons.Filled.PlayArrow)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Today's habits", Modifier.weight(1f))
                TextButton({ go(Routes.HABITS) }) { Text("See all", color = c.muted) }
            }
            if (due.isEmpty()) {
                AppCard {
                    EmptyState(Icons.Filled.CheckCircle, if (habits.isEmpty()) "No habits yet" else "Nothing due today",
                        if (habits.isEmpty()) "Add your first habit to start a streak." else "Enjoy your day off.") {
                        AppButton("Add habit", { go(Routes.habitEdit()) }, kind = ButtonKind.SECONDARY, fullWidth = false, icon = Icons.Filled.Add)
                    }
                }
            } else {
                due.forEach { h ->
                    val count = todayLogs[h.id]?.count ?: 0
                    HabitRow(
                        h, count,
                        subtitle = if (h.target > 1) "$count/${h.target} today" else if (count >= h.target) "Done" else "To do",
                        onCheck = { scope.launch { HabitActions.tap(context, h) } },
                        onOpen = { go(Routes.habitDetail(h.id)) },
                        streak = h.streak(logs, today).current,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colors
    AppCard(modifier, padding = 12.dp) {
        IconTile(icon, tint, 32.dp)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.foreground)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}
