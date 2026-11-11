package com.aravind.habitmaker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.ui.theme.colors

/** Habit icons by key (stored in the database as the key). */
val HabitIcons: Map<String, ImageVector> = linkedMapOf(
    "check" to Icons.Filled.CheckCircle,
    "read" to Icons.AutoMirrored.Filled.MenuBook,
    "study" to Icons.Filled.School,
    "workout" to Icons.Filled.FitnessCenter,
    "run" to Icons.Filled.DirectionsRun,
    "meditate" to Icons.Filled.SelfImprovement,
    "water" to Icons.Filled.LocalDrink,
    "sleep" to Icons.Filled.Bedtime,
    "morning" to Icons.Filled.WbSunny,
    "journal" to Icons.Filled.EditNote,
    "code" to Icons.Filled.Code,
    "music" to Icons.Filled.MusicNote,
    "art" to Icons.Filled.Brush,
    "eat" to Icons.Filled.Restaurant,
    "save" to Icons.Filled.Savings,
    "nophone" to Icons.Filled.PhoneDisabled,
)

fun habitIcon(key: String): ImageVector = HabitIcons[key] ?: Icons.Filled.CheckCircle

val HabitColors: List<Long> = listOf(
    0xFF10B981, 0xFF3B82F6, 0xFF8B5CF6, 0xFFF43F5E, 0xFFF59E0B, 0xFF06B6D4, 0xFFEC4899, 0xFF71717A,
)

/** An installed app's launcher icon, loaded off the main thread. */
@Composable
fun AppIcon(pkg: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bmp by produceState(initialValue = null as android.graphics.Bitmap?, pkg) { value = context.app.apps.icon(pkg) }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, Modifier.size(size).clip(MaterialTheme.shapes.small))
    } else {
        IconTile(Icons.Filled.Android, MaterialTheme.colors.muted, size)
    }
}

/** Equal-width tabs in a sunken track (System / Light / Dark, Today / Week …). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colors
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.sunken).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .background(if (on) c.surface else c.sunken)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.foreground else c.muted)
            }
        }
    }
}

/** A pill that can be selected (durations, goals …). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colors
    Box(
        modifier.clip(MaterialTheme.shapes.extraLarge)
            .background(if (selected) c.primary else c.surface)
            .border(1.dp, if (selected) c.primary else c.border, MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onPrimary else c.foreground)
    }
}

/** M T W T F S S toggles over a weekday mask. */
@Composable
fun DayPicker(mask: Int, onChange: (Int) -> Unit) {
    val c = MaterialTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Dates.SHORT_DAYS.forEachIndexed { i, label ->
            val on = mask and (1 shl i) != 0
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (on) c.primary else c.sunken)
                    .clickable { onChange(mask xor (1 shl i)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onPrimary else c.muted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, minuteOfDay: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colors
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = com.aravind.habitmaker.core.util.Clock.use24)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            TimeInput(
                state,
                colors = TimePickerDefaults.colors(
                    timeSelectorSelectedContainerColor = c.sunken,
                    timeSelectorSelectedContentColor = c.foreground,
                    timeSelectorUnselectedContainerColor = c.sunken,
                    timeSelectorUnselectedContentColor = c.muted,
                ),
            )
        },
        confirmButton = { TextButton({ onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("OK", color = c.foreground, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

/** Simple vertical bars with a label under each; [highlight] marks one bar (e.g. today). */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, highlight: Int = values.lastIndex, height: Dp = 140.dp) {
    val c = MaterialTheme.colors
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.55f
            values.forEachIndexed { i, v ->
                val h = (v / max) * size.height
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(c.sunken, Offset(x, 0f), Size(barW, size.height), CornerRadius(8f, 8f))
                if (h > 0f) {
                    drawRoundRect(
                        if (i == highlight) c.primary else c.primary.copy(alpha = 0.35f),
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(8f, 8f),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = if (i == highlight) c.foreground else c.faint, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Number with a small label under it, for stat rows. */
@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val c = MaterialTheme.colors
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = tint ?: c.foreground)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}
