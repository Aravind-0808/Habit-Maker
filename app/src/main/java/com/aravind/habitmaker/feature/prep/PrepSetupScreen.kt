package com.aravind.habitmaker.feature.prep

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.app
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.core.permissions.Perm
import com.aravind.habitmaker.core.permissions.Permissions
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.feature.blocking.AppChecklist
import com.aravind.habitmaker.feature.blocking.ShortVideoAppsCard
import com.aravind.habitmaker.feature.permissions.PermissionCards
import com.aravind.habitmaker.core.routine.EXAMS
import com.aravind.habitmaker.core.routine.Profile
import com.aravind.habitmaker.core.routine.RoutineGenerator
import com.aravind.habitmaker.core.routine.RoutineInput
import com.aravind.habitmaker.core.routine.TEMPLATES
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.db.ExamPlan
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.Chip
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Segmented
import com.aravind.habitmaker.ui.components.TimeDialog
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val STEPS = 9
private val STYLES = listOf(25 to 5, 45 to 15, 50 to 10, 90 to 20)

/** Questions → a generated timetable → save. Also used to change the plan later. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrepSetupScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var exam by remember { mutableStateOf("UGC NET") }
    var otherExam by remember { mutableStateOf("") }
    var examDay by remember { mutableStateOf<Long?>(null) }
    var profile by remember { mutableStateOf(Profile.STUDENT) }
    var wake by remember { mutableIntStateOf(5 * 60) }
    var sleep by remember { mutableIntStateOf(22 * 60) }
    var workStart by remember { mutableIntStateOf(9 * 60 + 30) }
    var workEnd by remember { mutableIntStateOf(18 * 60) }
    var eveningFirst by remember { mutableStateOf(false) }
    var studyMin by remember { mutableIntStateOf(180) }
    var session by remember { mutableIntStateOf(45) }
    var pause by remember { mutableIntStateOf(15) }
    var autoFocus by remember { mutableStateOf(true) }
    var strict by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val settings by context.app.prefs.settings.collectAsStateWithLifecycle(Settings())

    // Changing an existing plan: start from it.
    LaunchedEffect(Unit) {
        context.app.db.routine().plan()?.let { p ->
            editing = true
            if (p.exam in EXAMS) exam = p.exam else { exam = "Other"; otherExam = p.exam }
            examDay = p.examDay; profile = runCatching { Profile.valueOf(p.profile) }.getOrDefault(Profile.STUDENT)
            wake = p.wake; sleep = p.sleep; workStart = p.workStart; workEnd = p.workEnd
            studyMin = p.studyMin; session = p.sessionMin; pause = p.breakMin; autoFocus = p.autoFocus; strict = p.strictFocus
            step = 1
        }
    }

    fun input() = RoutineInput(profile, wake, sleep, workStart, workEnd, studyMin, session, pause, eveningFirst)
    fun applyTemplate(i: RoutineInput) {
        profile = i.profile; wake = i.wake; sleep = i.sleep; workStart = i.workStart; workEnd = i.workEnd
        studyMin = i.studyMin; session = i.sessionMin; pause = i.breakMin; eveningFirst = i.eveningFirst
    }

    when (pickTime) {
        "wake" -> TimeDialog("Wake-up time", wake, { pickTime = null }) { wake = it }
        "sleep" -> TimeDialog("Sleep time", sleep, { pickTime = null }) { sleep = it }
        "workStart" -> TimeDialog("Busy from", workStart, { pickTime = null }) { workStart = it }
        "workEnd" -> TimeDialog("Busy until", workEnd, { pickTime = null }) { workEnd = it }
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (examDay ?: LocalDate.now().plusMonths(3).toEpochDay()) * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { examDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickDate = false
                }) { Text("OK", color = c.foreground, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel", color = c.muted) } },
        ) { DatePicker(state) }
    }

    val examName = if (exam == "Other") otherExam.trim() else exam
    val canNext = when (step) {
        1 -> examName.isNotEmpty()
        else -> true
    }

    ScrollScreen(if (editing) "Change plan" else "Set up Exam Prep", onBack = { if (step > 0 && !(editing && step == 1)) step-- else onBack() }) {
        StepBar(step)

        when (step) {
            0 -> {
                Title("Start from a ready-made routine", "Or build your own. You can change everything later.")
                TEMPLATES.forEach { t ->
                    AppCard(onClick = { applyTemplate(t.input); step = 1 }) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text(t.detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                AppButton("Build my own", { step = 1 }, kind = ButtonKind.SECONDARY)
            }
            1 -> {
                Title("Which exam are you preparing for?", "We'll count down the days for you.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (EXAMS + "Other").forEach { e -> Chip(e, exam == e, { exam = e }) }
                }
                if (exam == "Other") AppTextField(otherExam, { otherExam = it }, "Exam name", placeholder = "e.g. CTET")
                AppCard(onClick = { pickDate = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Event, null, tint = c.muted)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Exam date", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                            Text(examDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMMM yyyy")) } ?: "Not set (optional)",
                                style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        if (examDay != null) TextButton({ examDay = null }) { Text("Clear", color = c.muted) }
                    }
                }
            }
            2 -> {
                Title("What does your day look like?", "So study time goes where you're free.")
                Profile.entries.forEach { p ->
                    AppCard(onClick = { profile = p }, borderColor = if (profile == p) c.foreground else null) {
                        Text(p.label, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                        Text(p.detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
            3 -> {
                Title("Your times", "When you get up, sleep and are busy.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeCard("Wake up", wake, Modifier.weight(1f)) { pickTime = "wake" }
                    TimeCard("Sleep", sleep, Modifier.weight(1f)) { pickTime = "sleep" }
                }
                if (profile != Profile.STUDENT) {
                    SectionLabel(if (profile == Profile.WORKING) "Work hours" else "Busy hours")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeCard("From", workStart, Modifier.weight(1f)) { pickTime = "workStart" }
                        TimeCard("Until", workEnd, Modifier.weight(1f)) { pickTime = "workEnd" }
                    }
                    SectionLabel("Study mostly in the")
                    Segmented(listOf(false to "Morning", true to "Evening"), eveningFirst, { eveningFirst = it })
                }
            }
            4 -> {
                Title("How much will you study a day?", "Pick a style: study time + break time.")
                Text(formatMinutes(studyMin), style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 120, 180, 240, 360, 480).forEach { m -> Chip("${m / 60} h", studyMin == m, { studyMin = m }) }
                }
                Slider(studyMin.toFloat(), { studyMin = (it / 15).toInt() * 15 }, valueRange = 30f..720f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken))
                SectionLabel("Study style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    STYLES.forEach { (s, b) -> Chip("$s + $b min", session == s && pause == b, { session = s; pause = b }) }
                }
                Text("Study $session min", style = MaterialTheme.typography.bodyMedium, color = c.foreground)
                Slider(session.toFloat(), { session = (it / 5).toInt() * 5 }, valueRange = 15f..120f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken))
                Text("Break $pause min", style = MaterialTheme.typography.bodyMedium, color = c.foreground)
                Slider(pause.toFloat(), { pause = it.toInt().coerceAtLeast(5) }, valueRange = 5f..30f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken))
                Text("After every 3 hours of study you get a 45-minute long break.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            5 -> {
                Title("Stay focused", "What happens when a study session starts.")
                AppCard {
                    ToggleRow("Start focus timer automatically", "Distracting apps are blocked while you study. A session counts as done only when its timer finishes.", autoFocus) { autoFocus = it }
                    Spacer(Modifier.height(12.dp))
                    ToggleRow("Strict study sessions", "Can't stop early, and Habit Maker can't be uninstalled meanwhile.", strict) { strict = it }
                }
                InlineBanner("The wake-up alarm rings with your chosen sound. Change sound, vibration and flash blink in Exam Prep → Alarm.", Tone.INFO)
            }
            6 -> {
                Title("Block apps while studying", "Blocked during every study session. The same list is used by the Block tab.")
                AppCard {
                    ToggleRow("Block during study and focus", "Turn off to study without blocking.", settings.blockDuringFocus) { on ->
                        scope.launch { context.app.prefs.update { it.copy(blockDuringFocus = on) } }
                    }
                }
                if (settings.blockDuringFocus) {
                    SectionLabel("YouTube & Instagram")
                    ShortVideoAppsCard()
                    SectionLabel("Other apps")
                    AppChecklist()
                }
            }
            7 -> {
                Title("Allow permissions", "So alarms ring on time, reminders show and apps get blocked.")
                PermissionCards(
                    listOf(Perm.NOTIFICATIONS, Perm.EXACT_ALARM, Perm.FULL_SCREEN, Perm.ACCESSIBILITY, Perm.BATTERY) +
                        if (Permissions.isXiaomi) listOf(Perm.AUTOSTART) else emptyList(),
                )
                Text("You can change these any time in Profile → Permissions.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            else -> {
                val g = RoutineGenerator.generate(input())
                Title("Your daily routine", "You can edit any block after saving.")
                if (g.unplaced > 0) InlineBanner("${formatMinutes(g.unplaced)} of study didn't fit in your free time. Wake up earlier, sleep later or study less.", Tone.WARNING)
                AppCard(padding = 0.dp) {
                    g.blocks.forEachIndexed { i, d ->
                        if (i > 0) com.aravind.habitmaker.ui.components.Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${formatClock(d.start)}–${formatClock(d.end)}", style = MaterialTheme.typography.labelLarge, color = c.foreground, modifier = Modifier.width(if (com.aravind.habitmaker.core.util.Clock.use24) 104.dp else 150.dp))
                            IconTile(d.type.icon(), d.type.tint(), 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(d.title, style = MaterialTheme.typography.bodyMedium, color = c.foreground)
                        }
                    }
                }
                if (editing) InlineBanner("Saving replaces your current routine. Your done / missed history is kept.", Tone.INFO)
                AppButton("Save and start", {
                    saving = true
                    scope.launch {
                        val plan = ExamPlan(
                            exam = examName, examDay = examDay, profile = profile.name, wake = wake, sleep = sleep,
                            workStart = workStart, workEnd = workEnd, studyMin = studyMin, sessionMin = session, breakMin = pause,
                            autoFocus = autoFocus, strictFocus = strict,
                        )
                        RoutineActions.applyPlan(context, plan, g.blocks)
                        onDone()
                    }
                }, loading = saving, loadingText = "Saving…")
            }
        }

        if (step in 1 until STEPS - 1) {
            AppButton("Next", { step++ }, enabled = canNext)
        }
    }
}

@Composable
private fun StepBar(step: Int) {
    val c = MaterialTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(STEPS) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) c.primary else c.sunken))
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    val c = MaterialTheme.colors
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.foreground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

@Composable
private fun TimeCard(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colors
    AppCard(modifier, onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text(formatClock(minute), style = MaterialTheme.typography.headlineSmall, color = c.foreground)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
    }
}
