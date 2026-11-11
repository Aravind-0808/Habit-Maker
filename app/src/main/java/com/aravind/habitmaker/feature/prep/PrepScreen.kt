package com.aravind.habitmaker.feature.prep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.routine.BlockType
import com.aravind.habitmaker.core.routine.DayResult
import com.aravind.habitmaker.core.routine.TEMPLATES
import com.aravind.habitmaker.core.routine.contains
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.routine.minutes
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.data.db.RoutineLog
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.ProgressBar
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Exam prep: countdown, today's routine with done / missed, and links to everything else. */
@Composable
fun PrepScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val plan by remember { app.db.routine().observePlan() }.collectAsStateWithLifecycle(null)
    val loaded by produceState(false) { app.db.routine().plan(); value = true }
    val blocks by remember { app.db.routine().observeBlocks() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember { app.db.routine().observeLogsSince(LocalDate.now().minusDays(60).toEpochDay()) }.collectAsStateWithLifecycle(emptyList())
    val run by FocusTimer.run.collectAsStateWithLifecycle()
    val now by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(30_000) } }
    var picked by remember { mutableStateOf<RoutineBlock?>(null) }

    val p = plan
    if (p == null) {
        ScrollScreen("Exam Prep") { if (loaded) Intro(go) }
        return
    }

    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val todayBlocks = blocks.on(today)
    val todayLogs = logs.filter { it.day == todayDay }.associateBy { it.blockId }
    val study = todayBlocks.filter { it.kind.isStudy }
    val doneStudy = study.filter { todayLogs[it.id]?.status == RoutineLog.DONE }
    val minute = now.hour * 60 + now.minute
    val current = todayBlocks.current(minute)
    val next = todayBlocks.next(minute)
    val statuses = dayStatuses(blocks, logs, today.minusDays(60), today)
    val streak = DayResult.streak(statuses.values.reversed())
    val daysLeft = p.examDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }

    picked?.let { b ->
        val mark = todayLogs[b.id]?.status
        val inTime = b.contains(minute)
        val early = !inTime && b.start > minute
        // A study session's focus can only run during its own time slot.
        val canFocus = b.kind.isStudy && inTime && run == null && mark != RoutineLog.DONE
        val warn = b.kind.isStudy && !inTime && mark != RoutineLog.DONE
        AlertDialog(
            onDismissRequest = { picked = null },
            containerColor = c.surface,
            icon = { IconTile(if (warn) Icons.Filled.Warning else b.kind.icon(), if (warn) c.warning else b.kind.tint(), 44.dp) },
            title = { Text(if (warn) "Not study time for this session" else b.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    when {
                        warn && early -> "\"${b.title}\" is set for ${formatClock(b.start)}–${formatClock(b.end)}. " +
                            "Its focus timer can only run during that time — it starts by itself at ${formatClock(b.start)}."
                        warn -> "\"${b.title}\" was set for ${formatClock(b.start)}–${formatClock(b.end)} and that time is over. " +
                            "The focus timer can't run for it now. To study at another time, change the session's time in Edit."
                        else -> "${formatClock(b.start)}–${formatClock(b.end)} · ${formatMinutes(b.minutes())}\n\n" + when {
                            mark == RoutineLog.DONE -> "✓ Done today."
                            mark == RoutineLog.MISSED -> "✗ Missed today."
                            b.kind.isStudy -> "Marked done only when you finish this session's focus timer. Stop early or skip it and it's missed."
                            b.kind == BlockType.WAKE -> "Marked done when you press Stop on the alarm. If nobody stops it, it's missed."
                            else -> "Nothing to mark — just follow the routine."
                        }
                    },
                    color = c.muted,
                )
            },
            confirmButton = {
                if (canFocus) TextButton({
                    FocusTimer.start(context, minutesLeft(b, minute), b.title, strict = p.strictFocus, blockId = b.id)
                    picked = null
                    go(Routes.FOCUS)
                }) { Text("Start focus (${formatMinutes(minutesLeft(b, minute))})", color = c.foreground, fontWeight = FontWeight.SemiBold) }
                else TextButton({ picked = null }) { Text("OK", color = c.foreground, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                if (canFocus) TextButton({ picked = null }) { Text("Close", color = c.muted) }
                else if (warn && !early) TextButton({ picked = null; go(Routes.routineBlock(b.id)) }) { Text("Change time", color = c.muted) }
            },
        )
    }

    ScrollScreen("Exam Prep", actions = {
        IconButton({ go(Routes.PREP_CALENDAR) }) { Icon(Icons.Filled.CalendarMonth, "Calendar", tint = c.foreground) }
        IconButton({ go(Routes.ALARM_SETTINGS) }) { Icon(Icons.Filled.Alarm, "Alarm", tint = c.foreground) }
    }) {
        // Countdown hero
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))).padding(20.dp),
        ) {
            Text(p.exam, style = MaterialTheme.typography.labelMedium, color = c.onHero.copy(alpha = 0.75f))
            Text(
                when {
                    daysLeft == null -> "No exam date set"
                    daysLeft > 1 -> "$daysLeft days to go"
                    daysLeft == 1L -> "Exam tomorrow!"
                    daysLeft == 0L -> "Exam today — all the best!"
                    else -> "Exam done"
                },
                style = MaterialTheme.typography.headlineMedium, color = c.onHero,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = c.onHero)
                Text(" $streak-day streak", style = MaterialTheme.typography.titleSmall, color = c.onHero, modifier = Modifier.weight(1f))
                Text("${doneStudy.size}/${study.size} study blocks", style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (study.isEmpty()) 0f else doneStudy.size / study.size.toFloat(), color = c.onHero, trackColor = c.onHero.copy(alpha = 0.2f))
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatMinutes(doneStudy.sumOf { it.minutes() })} of ${formatMinutes(study.sumOf { it.minutes() })} studied today",
                style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.8f),
            )
        }

        // Now / next
        if (current != null || next != null) {
            AppCard {
                current?.let { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(b.kind.icon(), b.kind.tint())
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Now · until ${formatClock(b.end)}", style = MaterialTheme.typography.labelMedium, color = c.muted)
                            Text(b.title, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                        }
                        if (b.kind.isStudy && run == null) {
                            AppButton("Focus", { FocusTimer.start(context, minutesLeft(b, minute), b.title, strict = p.strictFocus, blockId = b.id); go(Routes.FOCUS) },
                                fullWidth = false, icon = Icons.Filled.PlayArrow)
                        }
                    }
                }
                if (current != null && next != null) Spacer(Modifier.height(10.dp))
                next?.let { b -> Text("Next: ${formatClock(b.start)} · ${b.title}", style = MaterialTheme.typography.bodySmall, color = c.muted) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Today's routine", Modifier.weight(1f))
                TextButton({ go(Routes.ROUTINE_EDIT) }) { Icon(Icons.Filled.Edit, null, tint = c.muted); Text(" Edit", color = c.muted) }
            }
            if (todayBlocks.isEmpty()) {
                AppCard { Text("No blocks today. Enjoy your day off, or add some in Edit.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            }
            AppCard(padding = 0.dp) {
                todayBlocks.forEachIndexed { i, b ->
                    if (i > 0) com.aravind.habitmaker.ui.components.Divider()
                    TimelineRow(b, todayLogs[b.id]?.status, isNow = b == current, past = isPast(b, minute)) { picked = b }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Calendar", { go(Routes.PREP_CALENDAR) }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Filled.CalendarMonth)
            AppButton("Change plan", { go(Routes.PREP_SETUP) }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Filled.Tune)
        }
    }
}

private fun minutesLeft(b: RoutineBlock, minute: Int): Int = ((b.end - minute + 24 * 60) % (24 * 60)).coerceAtLeast(1)

private fun isPast(b: RoutineBlock, minute: Int): Boolean = b.kind != BlockType.SLEEP && b.end <= minute && b.start < b.end

@Composable
private fun TimelineRow(b: RoutineBlock, status: Int?, isNow: Boolean, past: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (isNow) Modifier.background(c.primarySoft) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(if (com.aravind.habitmaker.core.util.Clock.use24) 56.dp else 76.dp)) {
            Text(formatClock(b.start), style = MaterialTheme.typography.labelLarge, color = if (past && status == null) c.faint else c.foreground)
            Text(formatClock(b.end), style = MaterialTheme.typography.labelMedium, color = c.faint)
        }
        IconTile(b.kind.icon(), b.kind.tint(), 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
            Text(formatMinutes(b.minutes()), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        when {
            status == RoutineLog.DONE -> Badge("Done", Tone.SUCCESS, Icons.Filled.Check)
            status == RoutineLog.MISSED -> Badge("Missed", Tone.DANGER, Icons.Filled.Close)
            isNow -> Badge("Now", Tone.INFO)
        }
    }
}

/** No plan yet: what Exam Prep does, with the templates. */
@Composable
private fun Intro(go: (String) -> Unit) {
    val c = MaterialTheme.colors
    AppCard {
        IconTile(Icons.Filled.School, c.foreground, 56.dp)
        Spacer(Modifier.height(16.dp))
        Text("Plan your exam preparation", style = MaterialTheme.typography.headlineSmall, color = c.foreground)
        Spacer(Modifier.height(6.dp))
        Text(
            "Answer a few questions and get a daily timetable: wake-up alarm, study sessions with breaks, and reminders. " +
                "Mark each session done or missed and see your progress on a calendar.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted,
        )
        Spacer(Modifier.height(16.dp))
        AppButton("Set up my routine", { go(Routes.PREP_SETUP) })
    }
    SectionLabel("Ready-made routines")
    TEMPLATES.forEach { t ->
        AppCard(onClick = { go(Routes.PREP_SETUP) }) {
            Text(t.name, style = MaterialTheme.typography.titleSmall, color = c.foreground)
            Text(t.detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}
