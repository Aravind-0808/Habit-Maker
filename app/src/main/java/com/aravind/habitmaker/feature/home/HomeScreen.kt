package com.aravind.habitmaker.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.IconButton
import com.aravind.habitmaker.ui.components.gymChip
import com.aravind.habitmaker.ui.components.Segmented
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.core.util.formatTimer
import com.aravind.habitmaker.feature.habits.HabitActions
import com.aravind.habitmaker.feature.habits.HabitRow
import com.aravind.habitmaker.feature.habits.dueOn
import com.aravind.habitmaker.feature.habits.streak
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.ProgressBar
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.Avatar
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.EmptyState
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import com.aravind.habitmaker.ui.components.SkeletonScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.feature.prep.current
import com.aravind.habitmaker.feature.prep.icon
import com.aravind.habitmaker.feature.prep.next
import com.aravind.habitmaker.feature.prep.on
import com.aravind.habitmaker.feature.prep.tint

/** Today at a glance: screen time, focus, unlocks and today's habits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeScreen(go: (String) -> Unit, state: HomeState) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    val loadedUser by state.user.collectAsStateWithLifecycle()
    val loadedSettings by state.settings.collectAsStateWithLifecycle()
    val loadedData by state.data.collectAsStateWithLifecycle()
    val run by FocusTimer.run.collectAsStateWithLifecycle()

    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(state) {
        resumes++
        state.refreshUsage()
        onPauseOrDispose {}
    }
    val usage by state.usage.collectAsStateWithLifecycle()
    val hasAccess = remember(resumes) { app.usage.hasAccess() }

    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }

    // Show placeholders only until the first real snapshot arrives.
    val user = loadedUser ?: run { SkeletonScreen("Habit Maker"); return }
    val s = loadedSettings ?: run { SkeletonScreen("Habit Maker"); return }
    val (habits, logs, focus, plan, routine) = loadedData ?: run { SkeletonScreen("Habit Maker"); return }
    if (usage == null) { SkeletonScreen("Habit Maker"); return }

    val due = habits.filter { it.dueOn(today) }
    val todayLogs = logs.filter { it.day == today.toEpochDay() }.associateBy { it.habitId }
    val doneCount = due.count { (todayLogs[it.id]?.count ?: 0) >= it.target }
    val focusMin = focus.filter { com.aravind.habitmaker.core.util.Dates.dayOf(it.start) == today }.sumOf { it.actualMin }
    val bestStreak = habits.maxOfOrNull { it.streak(logs, today).current } ?: 0

    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    var graphMetric by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable { mutableIntStateOf(6) }
    val week = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val focusWeek = week.map { day -> focus.filter { com.aravind.habitmaker.core.util.Dates.dayOf(it.start) == day }.sumOf { it.actualMin }.toFloat() }
    val habitWeek = week.map { day ->
        val scheduled = habits.filter { it.dueOn(day) && com.aravind.habitmaker.core.util.Dates.dayOf(it.createdAt) <= day }
        if (scheduled.isEmpty()) 0f else scheduled.count { h -> logs.any { it.habitId == h.id && it.day == day.toEpochDay() && it.count >= h.target } } * 100f / scheduled.size
    }
    ScrollScreen("Today", actions = {
        IconButton({ state.refreshUsage() }) { Icon(Icons.Filled.Refresh, "Refresh screen time", tint = c.muted) }
        Avatar(user.initials, 36.dp) { go(Routes.PROFILE) }
        Spacer(Modifier.width(12.dp))
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM")), style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(if (user.name.isNotBlank()) "$greeting, ${user.name.substringBefore(' ')}" else greeting,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.foreground)
            }
            if (bestStreak > 0) Badge("${bestStreak}d streak", Tone.WARNING, Icons.Filled.LocalFireDepartment)
        }

        HomeFocusHero(focusMin, (s.defaultFocusMin * 2).coerceAtLeast(1), run, now, s.defaultFocusMin.coerceAtLeast(1)) {
            if (FocusTimer.run.value == null) FocusTimer.start(context, s.defaultFocusMin.coerceAtLeast(1), "Focus")
            go(Routes.FOCUS)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeSummaryTile("Screen time", if (hasAccess) formatDuration(usage?.totalMs ?: 0L) else "Connect",
                if (hasAccess) "Today · view app usage" else "Allow usage access", Icons.Filled.Smartphone,
                Modifier.weight(1f), c.secondary, if (hasAccess) ((usage?.totalMs ?: 0L).toFloat() / (s.dailyTargetMin.coerceAtLeast(1) * 60_000L)) else 0f) { go(if (hasAccess) Routes.STATS else Routes.PERMISSIONS) }
            HomeSummaryTile("Daily habits", "$doneCount / ${due.size}", if (due.isEmpty()) "None due today" else "Completed today",
                Icons.Filled.CheckCircle, Modifier.weight(1f), c.warning, if (due.isEmpty()) 0f else doneCount.toFloat() / due.size) { go(Routes.HABITS) }
        }

        // One primary graph, with real history and a readable selection for each day.
        AppCard(elevated = true, padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("YOUR MOMENTUM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c.primary)
                    Text("Your weekly rhythm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.foreground)
                }
                IconButton({ go(Routes.STATS) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "View full statistics", tint = c.primary) }
            }
            Spacer(Modifier.height(18.dp))
            Segmented(listOf(0 to "Focus time", 1 to "Habit progress"), graphMetric, { graphMetric = it })
            Spacer(Modifier.height(20.dp))
            val values = if (graphMetric == 0) focusWeek else habitWeek
            Text(if (values.all { it == 0f }) "A fresh week of possibilities" else if (graphMetric == 0) formatMinutes(values[selectedDay].toInt()) else "${values[selectedDay].toInt()}%",
                style = if (values.all { it == 0f }) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = c.foreground)
            Text(week[selectedDay].format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM")), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.height(18.dp))
            MomentumGraph(values, week, selectedDay, graphMetric == 1, { selectedDay = it })
            Spacer(Modifier.height(12.dp))
            Text(if (values.all { it == 0f }) "Your first ${if (graphMetric == 0) "focus session" else "habit check-in"} starts the story."
                else if (graphMetric == 0) "${formatMinutes(focusWeek.sum().toInt())} focused over the last 7 days" else "Daily completion of your scheduled habits",
                style = MaterialTheme.typography.bodySmall, color = c.muted)
        }

        if (s.blockNowUntil > now) {
            AppCard(onClick = { go(Routes.BLOCK) }, borderColor = c.primary.copy(alpha = 0.3f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Filled.Shield, c.primary, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Blocking session active", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text("${formatTimer((s.blockNowUntil - now) / 1000)} left • Tap to manage", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Manage blocking", tint = c.primary)
                }
            }
        }

        val p = plan
        if (p != null) {
            val todays = routine.on(today)
            val minute = LocalTime.now().let { it.hour * 60 + it.minute }
            val current = todays.current(minute)
            val next = todays.next(minute)
            val days = p.examDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }
            AppCard(onClick = { go(Routes.PREP) }, elevated = true, padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Filled.School, c.secondary, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(p.exam, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.foreground)
                        Text(days?.let { if (it >= 0) "$it days to go" else "Exam date has passed" } ?: "Your study routine", style = MaterialTheme.typography.bodySmall, color = c.secondary)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Open exam prep", tint = c.secondary)
                }
                Spacer(Modifier.height(14.dp))
                Text(current?.let { "Now · ${it.title} until ${formatClock(it.end)}" } ?: next?.let { "Up next · ${it.title} at ${formatClock(it.start)}" } ?: "Your routine is complete for today.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your daily wins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.foreground)
                    Text("$doneCount of ${due.size} completed", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                IconButton({ go(Routes.habitEdit()) }) { Icon(Icons.Filled.Add, "Add habit", tint = c.primary) }
            }
            val shown = due.sortedBy { (todayLogs[it.id]?.count ?: 0) >= it.target }.take(3)
            if (shown.isEmpty()) {
                AppCard {
                    EmptyState(Icons.Filled.CheckCircle,
                        when { due.isEmpty() -> if (habits.isEmpty()) "A fresh start" else "No habits due today"; else -> "You're all caught up" },
                        when { due.isEmpty() -> if (habits.isEmpty()) "Choose a small habit to build into your day." else "Your scheduled habits will appear on their next due day."; else -> "Every habit due today is complete. Nice work." }) {
                        AppButton(if (habits.isEmpty()) "Add a habit" else "View all habits", { go(if (habits.isEmpty()) Routes.habitEdit() else Routes.HABITS) }, kind = ButtonKind.SECONDARY)
                    }
                }
            } else {
                shown.forEach { h ->
                    androidx.compose.runtime.key(h.id) {
                        val count = todayLogs[h.id]?.count ?: 0
                        HabitRow(h, count,
                            subtitle = if (h.target > 1) "$count/${h.target} today" else if (count >= h.target) "Completed today" else "Tap the check to complete",
                            onCheck = { scope.launch { HabitActions.tap(context, h) } },
                            onOpen = { go(Routes.habitDetail(h.id)) }, streak = h.streak(logs, today).current)
                    }
                }
            }
            TextButton({ go(Routes.HABITS) }) { Text("Open habit tracker", color = c.primary) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Make it your day")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Block distractions", false, { go(Routes.BLOCK) })
                Chip(if (plan == null) "Plan my exam" else "My timetable", false, { go(if (plan == null) Routes.PREP_SETUP else Routes.PREP) })
                Chip("View insights", false, { go(Routes.STATS) })
            }
        }
    }
}

@Composable
private fun HomeSummaryTile(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier, tint: Color, progress: Float, onClick: () -> Unit) {
    val c = MaterialTheme.colors
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(tint.copy(alpha = if (c.isDark) 0.13f else 0.07f))
        .clickable(onClick = onClick).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, 32.dp)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.foreground)
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.foreground)
        ProgressBar(progress.coerceIn(0f, 1f), color = tint, trackColor = tint.copy(alpha = 0.12f))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}
