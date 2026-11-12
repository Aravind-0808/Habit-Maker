package com.aravind.habitmaker.feature.prep

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import com.aravind.habitmaker.ui.components.SkeletonLoading
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.routine.BlockType
import com.aravind.habitmaker.core.routine.DayResult
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
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Exam prep: countdown, today's routine with done / missed, and links to everything else. */
@Composable
fun PrepScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors

    // Null means the first database result is still loading, not that no plan exists.
    val prepData by remember(app) {
        val routine = app.db.routine()
        combine(
            routine.observePlan(),
            routine.observeBlocks(),
            routine.observeLogsSince(LocalDate.now().minusDays(60).toEpochDay()),
        ) { plan, blocks, logs -> Triple(plan, blocks, logs) }
    }.collectAsStateWithLifecycle(initialValue = null)
    val run by FocusTimer.run.collectAsStateWithLifecycle()
    val now by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(30_000) } }
    var picked by remember { mutableStateOf<RoutineBlock?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val loaded = prepData
    if (loaded == null) {
        ScrollScreen("Exam Prep") {
            SkeletonLoading()
        }
        return
    }
    val (p, blocks, logs) = loaded
    if (p == null) {
        ScrollScreen("Exam Prep") { Intro(go) }
        return
    }

    val today = LocalDate.now()
    val isToday = selectedDate == today
    val activeDayEpoch = selectedDate.toEpochDay()
    val activeBlocks = blocks.on(selectedDate)
    val activeLogs = logs.filter { it.day == activeDayEpoch }.associateBy { it.blockId }
    val study = activeBlocks.filter { it.kind.isStudy }
    val doneStudy = study.filter { activeLogs[it.id]?.status == RoutineLog.DONE }
    val minute = now.hour * 60 + now.minute
    val current = if (isToday) activeBlocks.current(minute) else null
    val next = if (isToday) activeBlocks.next(minute) else null
    val statuses = dayStatuses(blocks, logs, today.minusDays(60), today)
    val streak = DayResult.streak(statuses.values.reversed())
    val daysLeft = p.examDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }

    picked?.let { b ->
        val mark = activeLogs[b.id]?.status
        val inTime = b.contains(minute)
        val early = !inTime && b.start > minute
        val canFocus = isToday && b.kind.isStudy && inTime && run == null && mark != RoutineLog.DONE
        val warn = isToday && b.kind.isStudy && !inTime && mark != RoutineLog.DONE
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
                            mark == RoutineLog.DONE -> "✓ Done."
                            mark == RoutineLog.MISSED -> "✗ Missed."
                            b.kind.isStudy -> "Marked done only when you finish this session's focus timer. Stop early or skip it and it's missed."
                            b.kind == BlockType.WAKE -> "Marked done when you press Stop on the alarm."
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
        // Redesigned Exam Countdown & Target Card (matching exam_prep_schedule/code.html)
        AppCard(elevated = true, padding = 16.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.School, null, tint = c.primary, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("EXAM GOAL", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(p.exam, style = MaterialTheme.typography.titleMedium, color = c.foreground, fontWeight = FontWeight.Bold)
                    Text("Target: High Yield • ${streak}d Streak", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                IconButton({ go(Routes.PREP_SETUP) }) {
                    Icon(Icons.Filled.Tune, "Settings", tint = c.muted)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        daysLeft == null -> "—"
                        daysLeft >= 0 -> daysLeft.toString()
                        else -> "0"
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp, fontWeight = FontWeight.ExtraBold),
                    color = c.primary,
                )
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text("DAYS LEFT", style = MaterialTheme.typography.labelSmall, color = c.foreground, fontWeight = FontWeight.Bold)
                    Text(p.examDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "No date set", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }

            Spacer(Modifier.height(12.dp))
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Syllabus Mastery", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    val mastery = if (study.isNotEmpty()) (doneStudy.size.toFloat() / study.size * 100).toInt() else 0
                    Text("$mastery%", style = MaterialTheme.typography.labelMedium, color = c.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar(if (study.isNotEmpty()) doneStudy.size / study.size.toFloat() else 0f, color = c.primary, trackColor = c.sunken)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.sunken).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c.warningSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Speed, null, tint = c.warning, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Daily Study Target", style = MaterialTheme.typography.labelSmall, color = c.muted)
                        Text("${formatMinutes(doneStudy.sumOf { it.minutes() })} studied", style = MaterialTheme.typography.titleSmall, color = c.foreground, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.primarySoft).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = c.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${study.size} blocks", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Now / Next card if today
        if (isToday && (current != null || next != null)) {
            AppCard(elevated = true, padding = 14.dp) {
                current?.let { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(b.kind.icon(), b.kind.tint(), 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Now · Until ${formatClock(b.end)}", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
                            Text(b.title, style = MaterialTheme.typography.titleMedium, color = c.foreground, fontWeight = FontWeight.SemiBold)
                        }
                        if (b.kind.isStudy && run == null) {
                            AppButton("Focus", { FocusTimer.start(context, minutesLeft(b, minute), b.title, strict = p.strictFocus, blockId = b.id); go(Routes.FOCUS) },
                                fullWidth = false, kind = ButtonKind.PRIMARY, icon = Icons.Filled.PlayArrow)
                        }
                    }
                }
                if (current != null && next != null) Spacer(Modifier.height(10.dp))
                next?.let { b ->
                    Text("Next: ${formatClock(b.start)} · ${b.title}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }

        // Interactive 7-Day Selector Strip
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isToday) "TODAY'S ROUTINE" else "ROUTINE FOR ${selectedDate}", style = MaterialTheme.typography.labelSmall, color = c.muted, fontWeight = FontWeight.Bold)
                TextButton({ go(Routes.ROUTINE_EDIT) }) { Icon(Icons.Filled.Edit, null, tint = c.primary, modifier = Modifier.size(14.dp)); Text(" Edit", color = c.primary) }
            }

            // 7-day scroller
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (-3..3).forEach { offset ->
                    val d = today.plusDays(offset.toLong())
                    val isSelected = d == selectedDate
                    val dayInitial = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    val dayNum = d.dayOfMonth.toString()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) c.primary else c.surface)
                            .border(1.dp, if (isSelected) c.primary else c.border, RoundedCornerShape(12.dp))
                            .clickable { selectedDate = d }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(dayInitial, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (isSelected) c.onPrimary else c.muted)
                        Spacer(Modifier.height(2.dp))
                        Text(dayNum, style = MaterialTheme.typography.titleSmall, color = if (isSelected) c.onPrimary else c.foreground, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) c.onPrimary else c.border))
                    }
                }
            }

            // Connected Visual Timeline
            if (activeBlocks.isEmpty()) {
                AppCard { Text("No blocks scheduled for this day.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            } else {
                AppCard(padding = 0.dp) {
                    activeBlocks.forEachIndexed { i, b ->
                        val status = activeLogs[b.id]?.status
                        val isNow = isToday && b == current
                        val past = isToday && isPast(b, minute)
                        if (i > 0) com.aravind.habitmaker.ui.components.Divider()
                        ConnectedTimelineRow(b, status, isNow, past) { picked = b }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton("Calendar", { go(Routes.PREP_CALENDAR) }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Filled.CalendarMonth)
            AppButton("Change Plan", { go(Routes.PREP_SETUP) }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Filled.Tune)
        }
    }
}

private fun minutesLeft(b: RoutineBlock, minute: Int): Int = ((b.end - minute + 24 * 60) % (24 * 60)).coerceAtLeast(1)

private fun isPast(b: RoutineBlock, minute: Int): Boolean = b.kind != BlockType.SLEEP && b.end <= minute && b.start < b.end

@Composable
private fun ConnectedTimelineRow(b: RoutineBlock, status: Int?, isNow: Boolean, past: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (isNow) Modifier.background(c.primarySoft) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(if (com.aravind.habitmaker.core.util.Clock.use24) 56.dp else 76.dp)) {
            Text(formatClock(b.start), style = MaterialTheme.typography.labelLarge, color = if (past && status == null) c.faint else c.foreground)
            Text(formatClock(b.end), style = MaterialTheme.typography.labelMedium, color = c.faint)
        }
        IconTile(b.kind.icon(), b.kind.tint(), 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, style = MaterialTheme.typography.titleSmall, color = c.foreground, fontWeight = FontWeight.SemiBold)
            Text(formatMinutes(b.minutes()), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        when {
            status == RoutineLog.DONE -> Badge("Done", Tone.SUCCESS, Icons.Filled.Check)
            status == RoutineLog.MISSED -> Badge("Missed", Tone.DANGER, Icons.Filled.Close)
            isNow -> Badge("Now", Tone.INFO)
        }
    }
}

/** Welcome to the guided routine builder. */
@Composable
private fun Intro(go: (String) -> Unit) {
    val c = MaterialTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.School, c.primary, 52.dp)
                Badge("MADE FOR YOU", Tone.SUCCESS)
            }
            Text("Your goal.\nYour pace.\nYour study plan.", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = c.onHero)
            Text("Turn your exam goal into a daily routine you can follow. We'll find room for study, breaks and life.",
                style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.8f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("Study sessions", Tone.SUCCESS)
                Badge("Smart breaks", Tone.INFO)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("A little planning. A clearer day.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.foreground)
            Text("One simple choice at a time. Everything stays editable.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        AppCard(padding = 20.dp) {
            listOf(
                Triple(Icons.Filled.School, "Set your destination", "Choose your exam and a date to work towards."),
                Triple(Icons.Filled.Timer, "Find your rhythm", "Fit study around your day, with time to recharge."),
                Triple(Icons.Filled.CheckCircle, "Make it yours", "Preview your timetable before you start."),
            ).forEachIndexed { index, (icon, title, detail) ->
                if (index > 0) Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(icon, c.primary, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.foreground)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Text("0${index + 1}", style = MaterialTheme.typography.labelLarge, color = c.faint)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppButton("Build my study plan", { go(Routes.PREP_SETUP) }, icon = Icons.AutoMirrored.Filled.ArrowForward)
            Text("Start fresh or personalize a ready-made routine", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}
