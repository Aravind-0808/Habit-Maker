package com.aravind.habitmaker.feature.focus

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusRun
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.core.util.formatTime
import com.aravind.habitmaker.core.util.formatTimer
import com.aravind.habitmaker.data.db.FocusSession
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.Chip
import com.aravind.habitmaker.ui.components.Divider
import com.aravind.habitmaker.ui.components.EmptyState
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.Metric
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.Violet
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.ui.text.style.TextAlign
import com.aravind.habitmaker.core.routine.BlockType
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.feature.prep.current
import com.aravind.habitmaker.feature.prep.icon
import com.aravind.habitmaker.feature.prep.next
import com.aravind.habitmaker.feature.prep.on
import com.aravind.habitmaker.feature.prep.tint
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PRESETS = listOf(15, 25, 45, 60, 90)

/** Set up and run a focus session, then see the history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen() {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
    val run by FocusTimer.run.collectAsStateWithLifecycle()
    val result by FocusTimer.result.collectAsStateWithLifecycle()
    val sessions by remember { app.db.focus().observeRecent(30) }.collectAsStateWithLifecycle(emptyList())
    val plan by remember { app.db.routine().observePlan() }.collectAsStateWithLifecycle(null)
    val blocks by remember { app.db.routine().observeBlocks() }.collectAsStateWithLifecycle(emptyList())
    val clock by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(15_000) } }
    val minute = clock.hour * 60 + clock.minute
    val todays = if (plan != null) blocks.on(LocalDate.now()) else emptyList()
    val current = todays.current(minute)
    val upcoming = todays.next(minute)

    var minutes by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.defaultFocusMin) { if (minutes == 0) minutes = s.defaultFocusMin }
    var label by remember { mutableStateOf("") }
    var strict by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    result?.let { r ->
        val routine = r.blockId != null
        // In the routine: the next block is what comes after the one that just ended.
        val nextBlock = upcoming
        AlertDialog(
            onDismissRequest = { FocusTimer.clearResult() },
            containerColor = c.surface,
            icon = { IconTile(if (r.wasBreak) Icons.Filled.Timer else Icons.Filled.Coffee, Violet, 48.dp) },
            title = { Text(if (r.wasBreak) "Break's over" else if (r.completed) "Session complete!" else "Session stopped", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    when {
                        r.wasBreak && nextBlock != null -> "Next: ${nextBlock.title} at ${formatClock(nextBlock.start)}."
                        r.wasBreak -> "Ready for another round?"
                        routine && r.completed -> "You focused for ${formatMinutes(r.minutes)} and it's marked done. Time to take a break — stand up, drink water, rest your eyes."
                        else -> "You focused for ${formatMinutes(r.minutes)}."
                    },
                    color = c.muted,
                )
            },
            confirmButton = {
                val breakBlock = nextBlock?.takeIf { (it.kind == BlockType.BREAK || it.kind == BlockType.MEAL) && it.start <= minute + 1 }
                    ?: current?.takeIf { it.kind == BlockType.BREAK || it.kind == BlockType.MEAL }
                TextButton({
                    FocusTimer.clearResult()
                    when {
                        r.wasBreak && current != null && current.kind.isStudy ->
                            FocusTimer.start(context, minutesLeft(current, minute), current.title, strict = plan?.strictFocus == true, blockId = current.id)
                        r.wasBreak -> FocusTimer.start(context, minutes.coerceAtLeast(1), label, strict = strict)
                        breakBlock != null -> FocusTimer.start(context, minutesLeft(breakBlock, minute), breakBlock.title, isBreak = true, blockId = breakBlock.id)
                        else -> FocusTimer.start(context, s.breakMin, "Break", isBreak = true)
                    }
                }) {
                    Text(
                        when {
                            r.wasBreak -> "Start focus"
                            breakBlock != null -> "Start break (${formatMinutes(minutesLeft(breakBlock, minute))})"
                            else -> "Take a ${s.breakMin} min break"
                        },
                        color = c.foreground, fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = { TextButton({ FocusTimer.clearResult() }) { Text("Done", color = c.muted) } },
        )
    }

    ScrollScreen("Focus") {
        val r = run
        if (r != null) {
            Running(r, blocks.firstOrNull { it.id == r.blockId }, upcoming)
        } else {
            val p = plan
            if (p != null && current != null && (current.kind.isStudy || current.kind == BlockType.BREAK || current.kind == BlockType.MEAL)) {
                RoutineNow(current, minutesLeft(current, minute)) {
                    if (current.kind.isStudy) FocusTimer.start(context, minutesLeft(current, minute), current.title, strict = p.strictFocus, blockId = current.id)
                    else FocusTimer.start(context, minutesLeft(current, minute), current.title, isBreak = true, blockId = current.id)
                }
            }
            AppCard {
                Text("Focus for", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { m -> Chip("$m min", minutes == m, { minutes = m }) }
                }
                Slider(
                    minutes.toFloat(), { minutes = it.toInt().coerceAtLeast(1) }, valueRange = 1f..180f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken),
                )
                AppTextField(label, { label = it }, "What are you working on?", placeholder = "Optional")
                Spacer(Modifier.height(12.dp))
                ToggleRow("Block distracting apps", "Uses your list from the Block tab", s.blockDuringFocus) { on ->
                    scope.launch { app.prefs.update { it.copy(blockDuringFocus = on) } }
                }
                ToggleRow("Strict mode", "You can't stop the session early", strict) { strict = it }
                Spacer(Modifier.height(12.dp))
                AppButton("Start focus", {
                    if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    FocusTimer.start(context, minutes, label, strict = strict)
                }, icon = Icons.Filled.PlayArrow)
            }
        }

        History(sessions)
    }
}

/** Idle, but the routine says it's study or break time now. */
@Composable
private fun RoutineNow(b: RoutineBlock, left: Int, onStart: () -> Unit) {
    val c = MaterialTheme.colors
    val study = b.kind.isStudy
    AppCard(borderColor = c.primary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(b.kind.icon(), b.kind.tint())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Exam Prep · now until ${formatClock(b.end)}", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(b.title, style = MaterialTheme.typography.titleMedium, color = c.foreground)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (study) "It's study time — ${formatMinutes(left)} left in this session." else "It's break time — take a rest for ${formatMinutes(left)}.",
            style = MaterialTheme.typography.bodyMedium, color = if (study) c.foreground else c.success,
        )
        Spacer(Modifier.height(12.dp))
        AppButton(if (study) "Start focus (${formatMinutes(left)})" else "Start break timer", onStart, icon = Icons.Filled.PlayArrow)
    }
}

private fun minutesLeft(b: RoutineBlock, minute: Int): Int = ((b.end - minute + 24 * 60) % (24 * 60)).coerceAtLeast(1)

@Composable
private fun ToggleRow(title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
    }
}

@Composable
private fun Running(r: FocusRun, block: RoutineBlock?, upcoming: RoutineBlock?) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    val now by produceState(System.currentTimeMillis(), r) { while (true) { value = System.currentTimeMillis(); delay(250) } }
    val left = r.remainingMs(now)
    val progress = 1f - left.toFloat() / r.totalMs.coerceAtLeast(1)
    val ring = if (r.isBreak) c.success else c.foreground

    AppCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                if (block != null) { Spacer(Modifier.width(8.dp)); Badge("Exam Prep", Tone.INFO) }
                if (r.strict) { Spacer(Modifier.width(8.dp)); Badge("Strict", Tone.WARNING, Icons.Filled.Lock) }
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                val track = c.sunken
                Canvas(Modifier.size(240.dp)) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(ring, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatTimer(left / 1000), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 48.sp), color = c.foreground)
                    Text(if (r.paused) "Paused" else if (r.isBreak) "Break" else "Focus", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
            if (r.isBreak) {
                Spacer(Modifier.height(12.dp))
                Text("Time for a break. Stand up, stretch, drink water and rest your eyes — no scrolling!",
                    style = MaterialTheme.typography.bodyMedium, color = c.success, textAlign = TextAlign.Center)
            }
            if (block != null || upcoming != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    listOfNotNull(
                        block?.let { "Ends with your routine at ${formatClock(it.end)}" },
                        upcoming?.let { "Next: ${it.title} at ${formatClock(it.start)}" },
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (r.paused) AppButton("Resume", { FocusTimer.resume() }, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                else AppButton("Pause", { FocusTimer.pause() }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Filled.Pause, enabled = !r.strict)
                AppButton(if (r.isBreak) "Skip" else "Stop", { FocusTimer.stop(context) }, Modifier.weight(1f), kind = ButtonKind.DANGER, icon = Icons.Filled.Stop, enabled = !r.strict)
            }
            if (r.strict) {
                Spacer(Modifier.height(8.dp))
                Text("Strict mode: this session can't be paused or stopped.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

@Composable
private fun History(sessions: List<FocusSession>) {
    val c = MaterialTheme.colors
    val todayStart = remember { Dates.startOfToday() }
    val weekStart = remember { todayStart - 6 * 86_400_000L }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("History")
        AppCard {
            Row {
                Metric(formatMinutes(sessions.filter { it.start >= todayStart }.sumOf { it.actualMin }), "Today", Modifier.weight(1f))
                Metric(formatMinutes(sessions.filter { it.start >= weekStart }.sumOf { it.actualMin }), "Last 7 days", Modifier.weight(1f))
                Metric("${sessions.count { it.completed }}", "Completed", Modifier.weight(1f))
            }
        }
        if (sessions.isEmpty()) {
            AppCard { EmptyState(Icons.Filled.Timer, "No sessions yet", "Your finished focus sessions show up here.") }
        } else {
            AppCard(padding = 0.dp) {
                sessions.take(15).forEachIndexed { i, f ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.label, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                            Text(Instant.ofEpochMilli(f.start).atZone(ZoneId.systemDefault()).let { "${fmt.format(it)} · ${formatTime(it.toLocalTime())}" }, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        Text(formatMinutes(f.actualMin), style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Spacer(Modifier.width(8.dp))
                        Badge(if (f.completed) "Done" else "Stopped", if (f.completed) Tone.SUCCESS else Tone.NEUTRAL)
                    }
                }
            }
        }
    }
}
