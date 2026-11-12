package com.aravind.habitmaker.feature.prep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.aravind.habitmaker.ui.components.AppHeader
import com.aravind.habitmaker.ui.components.LoadingState
import kotlinx.coroutines.CancellationException
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
private val STEP_NAMES = listOf("Starting point", "Exam goal", "Your day", "Daily rhythm", "Study pace", "Focus", "Distractions", "Permissions", "Review")
private val STYLES = listOf(25 to 5, 45 to 15, 50 to 10, 90 to 20)

/** Questions → a generated timetable → save. Also used to change the plan later. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrepSetupScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableIntStateOf(0) }
    var exam by rememberSaveable { mutableStateOf("UGC NET") }
    var otherExam by rememberSaveable { mutableStateOf("") }
    var examDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var profile by rememberSaveable { mutableStateOf(Profile.STUDENT) }
    var wake by rememberSaveable { mutableIntStateOf(5 * 60) }
    var sleep by rememberSaveable { mutableIntStateOf(22 * 60) }
    var workStart by rememberSaveable { mutableIntStateOf(9 * 60 + 30) }
    var workEnd by rememberSaveable { mutableIntStateOf(18 * 60) }
    var eveningFirst by rememberSaveable { mutableStateOf(false) }
    var studyMin by rememberSaveable { mutableIntStateOf(180) }
    var session by rememberSaveable { mutableIntStateOf(45) }
    var pause by rememberSaveable { mutableIntStateOf(15) }
    var autoFocus by rememberSaveable { mutableStateOf(true) }
    var strict by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf<String?>(null) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var selectedTemplate by rememberSaveable { mutableIntStateOf(-1) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val settings by context.app.prefs.settings.collectAsStateWithLifecycle(Settings())

    // Changing an existing plan: start from it.
    LaunchedEffect(Unit) {
        if (initialized) return@LaunchedEffect
        context.app.db.routine().plan()?.let { p ->
            editing = true
            if (p.exam in EXAMS) exam = p.exam else { exam = "Other"; otherExam = p.exam }
            examDay = p.examDay; profile = runCatching { Profile.valueOf(p.profile) }.getOrDefault(Profile.STUDENT)
            wake = p.wake; sleep = p.sleep; workStart = p.workStart; workEnd = p.workEnd
            studyMin = p.studyMin; session = p.sessionMin; pause = p.breakMin; autoFocus = p.autoFocus; strict = p.strictFocus
            step = 1
        }
        initialized = true
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

    val back = { if (!saving) { if (step > 0 && !(editing && step == 1)) step-- else onBack() } }
    BackHandler(onBack = back)

    fun saveRoutine() {
        if (saving) return
        saving = true
        saveError = null
        scope.launch {
            try {
                val plan = ExamPlan(
                    exam = examName, examDay = examDay, profile = profile.name, wake = wake, sleep = sleep,
                    workStart = workStart, workEnd = workEnd, studyMin = studyMin, sessionMin = session, breakMin = pause,
                    autoFocus = autoFocus, strictFocus = strict,
                )
                RoutineActions.applyPlan(context, plan, RoutineGenerator.generate(input()).blocks)
                onDone()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveError = "Couldn't save your routine. Please try again."
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        AppHeader(if (editing) "Personalize your plan" else "Build your study routine", onBack = back)
        if (!initialized) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp)) { LoadingState() }
            return@Column
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { StepBar(step) }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 5 * direction } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 5 * direction } + fadeOut())
            },
            label = "Setup page",
        ) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (page) {
                    0 -> {
                        Title("A routine that fits your life.", "Choose a starting point. We'll tailor it to your exam, energy and free time.")
                        ChoiceCard("Build it my way", "Start fresh and choose your own study pace.", Icons.Filled.Tune, selectedTemplate == -1) {
                            selectedTemplate = -1
                            applyTemplate(RoutineInput(Profile.STUDENT, wake = 5 * 60, sleep = 22 * 60, studyMin = 180, sessionMin = 45, breakMin = 15))
                        }
                        SectionLabel("Or start with a template")
                        TEMPLATES.forEachIndexed { index, t ->
                            ChoiceCard(t.name, t.detail, Icons.Filled.Timer, selectedTemplate == index) {
                                selectedTemplate = index
                                applyTemplate(t.input)
                            }
                        }
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
                            ChoiceCard(p.label, p.detail, Icons.Filled.School, profile == p) { profile = p }
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
                        Text(formatMinutes(studyMin), style = MaterialTheme.typography.headlineMedium, color = c.primary)
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
                        AppCard(borderColor = c.primary.copy(alpha = 0.4f)) {
                            Text("Your study rhythm", style = MaterialTheme.typography.labelLarge, color = c.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("$session min focus  →  $pause min recharge", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                            Spacer(Modifier.height(6.dp))
                            Text("A 45-minute long break after every 3 hours of study. Your timetable updates with every choice.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
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
                        Title("Meet your new routine.", "Built around $examName and the way your day works. You can edit every block later.")
                        AppCard(borderColor = c.primary.copy(alpha = 0.4f), elevated = true) {
                            Text(examName, style = MaterialTheme.typography.titleLarge, color = c.foreground, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("${formatMinutes((studyMin - g.unplaced).coerceAtLeast(0))} study • $session min sessions • $pause min breaks", style = MaterialTheme.typography.bodyMedium, color = c.primary)
                            Spacer(Modifier.height(6.dp))
                            Text("${formatClock(wake)} wake-up  →  ${formatClock(sleep)} bedtime", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            TextButton({ step = 4 }) { Text("Adjust study pace", color = c.primary) }
                        }
                        if (g.unplaced > 0) InlineBanner("${formatMinutes(g.unplaced)} of study didn't fit in your free time. Wake up earlier, sleep later or study less.", Tone.WARNING)
                        AppCard(padding = 0.dp) {
                            g.blocks.forEachIndexed { i, d ->
                                if (i > 0) com.aravind.habitmaker.ui.components.Divider()
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(d.type.icon(), d.type.tint(), 34.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(d.title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                                        Text("${formatClock(d.start)} – ${formatClock(d.end)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                                    }
                                }
                            }
                        }
                        if (editing) InlineBanner("Saving replaces your current routine. Your done / missed history is kept.", Tone.INFO)

                    }
                }

            }
        }
        Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            saveError?.let { InlineBanner(it, Tone.DANGER) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) AppButton("Back", back, Modifier.weight(1f), kind = ButtonKind.SECONDARY, enabled = !saving)
                AppButton(
                    when (step) { 0 -> "Let's build my plan"; 7 -> "Preview my routine"; 8 -> if (editing) "Save changes" else "Start my routine"; else -> "Continue" },
                    { if (step == STEPS - 1) saveRoutine() else step++ },
                    Modifier.weight(if (step > 0) 2f else 1f), enabled = canNext && !saving,
                    loading = saving, loadingText = "Saving routine…",
                )
            }
            Text(if (step == 8) "Your routine will be saved on this device." else "${STEP_NAMES.getOrElse(step + 1) { "Review" }} is next", style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
    }
}

@Composable
private fun StepBar(step: Int) {
    val c = MaterialTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(STEP_NAMES[step].uppercase(), style = MaterialTheme.typography.labelMedium, color = c.primary, fontWeight = FontWeight.Bold)
            Text("${step + 1} of $STEPS", style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(STEPS) { i ->
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= step) c.primary else c.sunken))
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    val c = MaterialTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = c.foreground)
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

@Composable
private fun ChoiceCard(title: String, detail: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colors
    AppCard(
        modifier = Modifier.semantics { selected = isSelected },
        onClick = onClick,
        borderColor = if (isSelected) c.primary else c.border,
        elevated = isSelected,
        padding = 18.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, if (isSelected) c.primary else c.muted, 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.foreground)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Icon(if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                if (isSelected) "Selected" else null, tint = if (isSelected) c.primary else c.faint, modifier = Modifier.size(22.dp))
        }
    }
}
