package com.aravind.habitmaker.feature.habits

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import com.aravind.habitmaker.ui.components.Chip
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.components.SkeletonScreen
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.data.db.ALL_DAYS
import com.aravind.habitmaker.data.db.Habit
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.DayPicker
import com.aravind.habitmaker.ui.components.HabitColors
import com.aravind.habitmaker.ui.components.HabitIcons
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.TimeDialog
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

private val SUGGESTIONS = listOf(
    "Read 10 pages" to "read", "Drink water" to "water", "Meditate" to "meditate",
    "Workout" to "workout", "Study" to "study", "No phone in bed" to "nophone",
)

/** New habit ([habitId] = 0) or edit an existing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(habitId: Long, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<Habit?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("check") }
    var color by rememberSaveable { mutableLongStateOf(HabitColors.first()) }
    var days by rememberSaveable { mutableIntStateOf(ALL_DAYS) }
    var target by rememberSaveable { mutableIntStateOf(1) }
    var reminderOn by rememberSaveable { mutableStateOf(false) }
    var reminder by rememberSaveable { mutableIntStateOf(8 * 60) }
    var timing by rememberSaveable { mutableStateOf("ANYTIME") }
    var end by rememberSaveable { mutableIntStateOf(20 * 60) }
    var interval by rememberSaveable { mutableIntStateOf(120) }
    var pickTime by rememberSaveable { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(habitId != 0L) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var tried by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(habitId) {
        if (habitId == 0L) { initialized = true; loading = false; return@LaunchedEffect }
        val h = context.app.db.habits().get(habitId)
        loaded = h
        if (!initialized && h != null) {
            name = h.name; icon = h.icon; color = h.color; days = h.days; target = h.target
            reminderOn = h.reminderMinute != null
            reminder = h.startMinute ?: h.reminderMinute ?: reminder
            end = h.endMinute ?: end; interval = h.intervalMin ?: interval
            timing = when { h.timing != "ANYTIME" -> h.timing; h.target > 1 -> "REPEAT"; h.reminderMinute != null -> "ONCE"; else -> "ANYTIME" }
        }
        initialized = true
        loading = false
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    pickTime?.let { field ->
        TimeDialog(if (field == "end") "Until" else if (timing == "ONCE") "Habit time" else "From", if (field == "end") end else reminder, { pickTime = null }) {
            if (field == "end") end = it else reminder = it
        }
    }
    if (loading) { SkeletonScreen("Edit habit", onBack); return }
    val invalidWindow = timing in listOf("WINDOW", "REPEAT") && end <= reminder

    ScrollScreen(if (habitId == 0L) "New habit" else "Edit habit", onBack = onBack) {
        AppTextField(name, { name = it }, "Name", placeholder = "e.g. Read 10 pages",
            error = if (tried && name.isBlank()) "Give your habit a name" else null)

        if (habitId == 0L && name.isBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { (label, key) ->
                    com.aravind.habitmaker.ui.components.Chip(label, false, {
                        name = label; icon = key
                        when (key) {
                            "water" -> { timing = "REPEAT"; target = 6; reminder = 8 * 60; end = 20 * 60; interval = 120 }
                            "study", "workout" -> { timing = "WINDOW"; reminder = 18 * 60; end = 19 * 60 }
                            "meditate" -> { timing = "ONCE"; reminder = 8 * 60 }
                            "nophone" -> { timing = "ONCE"; reminder = 22 * 60 }
                        }
                    })
                }
            }
        }

        Column {
            SectionLabel("Repeat on")
            DayPicker(days) { days = it }
            if (tried && days == 0) Text("Pick at least one day", style = MaterialTheme.typography.bodySmall, color = c.danger)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("When will you do it?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ANYTIME" to "Anytime", "ONCE" to "At a time", "WINDOW" to "From–to", "REPEAT" to "Repeat").forEach { (value, label) ->
                    Chip(label, timing == value, { timing = value })
                }
            }
            Text(when (timing) {
                "ONCE" -> "One reminder, one check-in. Great for yoga or meditation."
                "WINDOW" -> "Reserve a time slot for study or work. Check it off once when finished."
                "REPEAT" -> "For habits like drinking water: set your daily count and how often to remind you."
                else -> "Do it whenever it fits your day. No reminders or daily frequency needed."
            }, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (timing != "ANYTIME") {
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppCard(Modifier.weight(1f), onClick = { pickTime = "start" }) {
                        Text(if (timing == "ONCE") "At" else "From", style = MaterialTheme.typography.labelMedium, color = c.muted)
                        Text(formatClock(reminder), style = MaterialTheme.typography.titleLarge, color = c.foreground)
                    }
                    if (timing != "ONCE") AppCard(Modifier.weight(1f), onClick = { pickTime = "end" }) {
                        Text("Until", style = MaterialTheme.typography.labelMedium, color = c.muted)
                        Text(formatClock(end), style = MaterialTheme.typography.titleLarge, color = c.foreground)
                    }
                }
                if (invalidWindow && tried) {
                    Spacer(Modifier.height(10.dp))
                    Text("Until must be later than From on the same day.", style = MaterialTheme.typography.bodySmall, color = c.danger)
                }
                if (timing == "REPEAT") {
                    Spacer(Modifier.height(18.dp))
                    Text("Daily check-ins", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$target times per day", style = MaterialTheme.typography.bodyMedium, color = c.foreground, modifier = Modifier.weight(1f))
                        IconButton({ if (target > 1) target-- }) { Icon(Icons.Filled.Remove, "Fewer check-ins", tint = c.foreground) }
                        IconButton({ if (target < 20) target++ }) { Icon(Icons.Filled.Add, "More check-ins", tint = c.foreground) }
                    }
                    Text("Remind me every", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120, 180, 240).forEach { mins -> Chip(if (mins < 60) "$mins min" else "${mins / 60} h", interval == mins, { interval = mins }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Reminders run only inside this window and stop notifying once your daily count is complete.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Alarm, null, tint = c.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (timing == "REPEAT") "Repeat reminders" else if (timing == "WINDOW") "Remind me at the start" else "Remind me at this time", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text(if (reminderOn) "On" else "Off", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Switch(reminderOn, {
                        reminderOn = it
                        if (it && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
                }
            }
        }
        AppCard {
            Text("Your plan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = c.primary)
            Spacer(Modifier.height(8.dp))
            Text(when (timing) {
                "ONCE" -> "Once on each selected day at ${formatClock(reminder)}."
                "WINDOW" -> "${formatClock(reminder)} – ${formatClock(end)}. One check-in when you're done."
                "REPEAT" -> "$target check-ins per selected day. " + if (reminderOn) "Reminders every $interval minutes, ${formatClock(reminder)} – ${formatClock(end)}." else "Reminders are off."
                else -> "Once on each selected day, whenever you're ready."
            }, style = MaterialTheme.typography.bodyMedium, color = c.foreground)
        }

        Column {
            SectionLabel("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitIcons.forEach { (key, vector) ->
                    val on = key == icon
                    Box(
                        Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                            .background(if (on) c.primarySoft else c.sunken)
                            .border(2.dp, if (on) c.primary else Color.Transparent, MaterialTheme.shapes.medium)
                            .clickable { icon = key },
                        contentAlignment = Alignment.Center,
                    ) { Icon(vector, key, tint = if (on) c.primary else c.muted) }
                }
            }
        }

        saveError?.let { InlineBanner(it, Tone.DANGER) }
        AppButton("Save habit", {
            tried = true
            if (name.isBlank() || days == 0 || invalidWindow || saving) return@AppButton
            if (habitId != 0L && loaded == null) { saveError = "This habit no longer exists."; return@AppButton }
            val base = loaded ?: Habit(name = "", icon = icon, color = color)
            val h = base.copy(
                name = name.trim(), icon = icon, color = color, days = days,
                target = if (timing == "REPEAT") target else 1,
                timing = timing,
                startMinute = if (timing == "ANYTIME") null else reminder,
                endMinute = if (timing in listOf("WINDOW", "REPEAT")) end else null,
                intervalMin = if (timing == "REPEAT") interval else null,
                reminderMinute = if (reminderOn && timing != "ANYTIME") reminder else null,
            )
            saving = true
            saveError = null
            scope.launch {
                try { HabitActions.save(context, h); onSaved() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { saveError = "Couldn't save the habit. Please try again." }
                finally { saving = false }
            }
        }, loading = saving, loadingText = "Saving…")
    }
}
