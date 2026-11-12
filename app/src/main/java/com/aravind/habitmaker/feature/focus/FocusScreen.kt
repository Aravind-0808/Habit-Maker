package com.aravind.habitmaker.feature.focus

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import com.aravind.habitmaker.ui.components.AnimatedNumber
import com.aravind.habitmaker.ui.components.rememberBreathingGlow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.aravind.habitmaker.ui.components.SkeletonScreen
import kotlinx.coroutines.flow.combine
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen() {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
    val run by FocusTimer.run.collectAsStateWithLifecycle()
    val result by FocusTimer.result.collectAsStateWithLifecycle()
    val loadedFocus by remember(app) {
        combine(app.db.focus().observeRecent(30), app.db.routine().observePlan(), app.db.routine().observeBlocks()) {
            sessions, plan, blocks -> Triple(sessions, plan, blocks)
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    val clock by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(15_000) } }
    val minute = clock.hour * 60 + clock.minute
    val (sessions, plan, blocks) = loadedFocus ?: run { SkeletonScreen("Focus"); return }
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
        val breakBlock = nextBlock?.takeIf { (it.kind == BlockType.BREAK || it.kind == BlockType.MEAL) && it.start <= minute + 1 }
            ?: current?.takeIf { it.kind == BlockType.BREAK || it.kind == BlockType.MEAL }
        ModalBottomSheet(
            onDismissRequest = { FocusTimer.clearResult() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                IconTile(if (r.wasBreak) Icons.Filled.Coffee else Icons.Filled.Timer, c.primary, 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (r.wasBreak) "Ready for another round?" else if (r.completed) "Focus session complete" else "Session ended",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.foreground)
                    Text(
                        when {
                            r.wasBreak && nextBlock != null -> "Up next: ${nextBlock.title} at ${formatClock(nextBlock.start)}."
                            r.wasBreak -> "Your break is over. Start again when you're ready."
                            r.minutes == 0 -> "This session ended before a full minute. Start again whenever you're ready."
                            routine && r.completed -> "${formatMinutes(r.minutes)} focused. Your study block is marked done. Take a moment to recharge."
                            else -> "You focused for ${formatMinutes(r.minutes)}. Every bit of progress counts."
                        }, style = MaterialTheme.typography.bodyMedium, color = c.muted,
                    )
                }
                if (r.completed || r.wasBreak) {
                    AppButton(
                        when {
                            r.wasBreak -> "Start focus"
                            breakBlock != null -> "Start break · ${formatMinutes(minutesLeft(breakBlock, minute))}"
                            else -> "Take a ${s.breakMin} min break"
                        },
                        {
                            FocusTimer.clearResult()
                            when {
                                r.wasBreak && current != null && current.kind.isStudy -> FocusTimer.start(context, minutesLeft(current, minute), current.title, strict = plan?.strictFocus == true, blockId = current.id)
                                r.wasBreak -> FocusTimer.start(context, minutes.coerceAtLeast(1), label, strict = strict)
                                breakBlock != null -> FocusTimer.start(context, minutesLeft(breakBlock, minute), breakBlock.title, isBreak = true, blockId = breakBlock.id)
                                else -> FocusTimer.start(context, s.breakMin, "Break", isBreak = true)
                            }
                        },
                    )
                    AppButton("Done for now", { FocusTimer.clearResult() }, kind = ButtonKind.SECONDARY)
                } else {
                    AppButton("Back to focus", { FocusTimer.clearResult() })
                }
            }
        }
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
            AppCard(elevated = true, padding = 18.dp) {
                Text("FOCUS MODE & DURATION", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineMedium, color = c.foreground, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        25 to "Pomodoro (25m)",
                        5 to "Short Break (5m)",
                        15 to "Long Break (15m)",
                        60 to "Deep Work (60m)",
                        90 to "Sprint (90m)",
                    ).forEach { (m, title) ->
                        Chip(title, minutes == m, { minutes = m })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    minutes.toFloat(), { minutes = it.toInt().coerceAtLeast(1) }, valueRange = 1f..180f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken),
                )
                AppTextField(label, { label = it }, "Focus Subject / Task", placeholder = "e.g. Organic Chemistry • Problem Set #4")
                Spacer(Modifier.height(14.dp))
                ToggleRow("Block distracting apps", "Uses your list from the Block tab", s.blockDuringFocus) { on ->
                    scope.launch { app.prefs.update { it.copy(blockDuringFocus = on) } }
                }
                ToggleRow("Strict mode", "You can't stop the session early", strict) { strict = it }
                Spacer(Modifier.height(16.dp))
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
    val rawProgress = (1f - left.toFloat() / r.totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "timer_sweep",
    )
    val ringColor = if (r.isBreak) c.success else c.primary
    val ambientGlow = rememberBreathingGlow(minAlpha = 0.5f, maxAlpha = 0.95f, durationMs = 2600)

    AppCard(elevated = true, padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(r.label, style = MaterialTheme.typography.titleMedium, color = c.foreground, fontWeight = FontWeight.SemiBold)
                if (block != null) { Spacer(Modifier.width(8.dp)); Badge("Exam Prep", Tone.INFO) }
                if (r.strict) { Spacer(Modifier.width(8.dp)); Badge("Strict", Tone.WARNING, Icons.Filled.Lock) }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                val track = c.surfaceElevated
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (!r.paused) alpha = ambientGlow
                        }
                ) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(ringColor, -90f, 360f * animatedProgress, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(formatTimer(left / 1000)) { timeStr ->
                        Text(timeStr, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 46.sp, fontWeight = FontWeight.Bold), color = c.foreground)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (r.paused) "PAUSED" else if (r.isBreak) "BREAK TIME" else "DEEP FOCUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (r.paused) c.muted else ringColor,
                        fontWeight = FontWeight.Bold,
                    )
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
