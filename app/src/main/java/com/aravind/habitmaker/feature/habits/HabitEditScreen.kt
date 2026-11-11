package com.aravind.habitmaker.feature.habits

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("check") }
    var color by remember { mutableLongStateOf(HabitColors.first()) }
    var days by remember { mutableIntStateOf(ALL_DAYS) }
    var target by remember { mutableIntStateOf(1) }
    var reminderOn by remember { mutableStateOf(false) }
    var reminder by remember { mutableIntStateOf(8 * 60) }
    var pickTime by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    LaunchedEffect(habitId) {
        if (habitId == 0L) return@LaunchedEffect
        context.app.db.habits().get(habitId)?.let { h ->
            loaded = h; name = h.name; icon = h.icon; color = h.color; days = h.days; target = h.target
            reminderOn = h.reminderMinute != null; reminder = h.reminderMinute ?: reminder
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (pickTime) TimeDialog("Reminder time", reminder, { pickTime = false }) { reminder = it }

    ScrollScreen(if (habitId == 0L) "New habit" else "Edit habit", onBack = onBack) {
        AppTextField(name, { name = it }, "Name", placeholder = "e.g. Read 10 pages",
            error = if (tried && name.isBlank()) "Give your habit a name" else null)

        if (habitId == 0L && name.isBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { (label, key) ->
                    com.aravind.habitmaker.ui.components.Chip(label, false, { name = label; icon = key })
                }
            }
        }

        Column {
            SectionLabel("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitIcons.forEach { (key, vector) ->
                    val on = key == icon
                    Box(
                        Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                            .background(if (on) Color(color).copy(alpha = 0.18f) else c.sunken)
                            .border(2.dp, if (on) Color(color) else Color.Transparent, MaterialTheme.shapes.medium)
                            .clickable { icon = key },
                        contentAlignment = Alignment.Center,
                    ) { Icon(vector, key, tint = if (on) Color(color) else c.muted) }
                }
            }
        }

        Column {
            SectionLabel("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HabitColors.forEach { col ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(col))
                            .border(3.dp, if (col == color) c.foreground else Color.Transparent, CircleShape)
                            .clickable { color = col },
                    )
                }
            }
        }

        Column {
            SectionLabel("Repeat on")
            DayPicker(days) { days = it }
            if (tried && days == 0) Text("Pick at least one day", style = MaterialTheme.typography.bodySmall, color = c.danger)
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Times per day", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text("Tap the check this many times to finish", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                IconButton({ if (target > 1) target-- }) { Icon(Icons.Filled.Remove, "Less", tint = c.foreground) }
                Text("$target", style = MaterialTheme.typography.titleLarge, color = c.foreground)
                IconButton({ if (target < 20) target++ }) { Icon(Icons.Filled.Add, "More", tint = c.foreground) }
            }
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Alarm, null, tint = c.muted)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reminder", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text(if (reminderOn) formatClock(reminder) else "Off", style = MaterialTheme.typography.bodySmall, color = c.muted,
                        modifier = if (reminderOn) Modifier.clickable { pickTime = true } else Modifier)
                }
                Switch(
                    reminderOn,
                    {
                        reminderOn = it
                        if (it) {
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            pickTime = true
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
                )
            }
        }

        AppButton("Save", {
            tried = true
            if (name.isBlank() || days == 0) return@AppButton
            val base = loaded ?: Habit(name = "", icon = icon, color = color)
            val h = base.copy(name = name.trim(), icon = icon, color = color, days = days, target = target, reminderMinute = if (reminderOn) reminder else null)
            scope.launch { HabitActions.save(context, h); onSaved() }
        })
    }
}
