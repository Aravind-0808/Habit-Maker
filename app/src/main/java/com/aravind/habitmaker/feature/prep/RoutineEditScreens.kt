package com.aravind.habitmaker.feature.prep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.routine.BlockType
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.util.daysLabel
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.db.ALL_DAYS
import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.Chip
import com.aravind.habitmaker.ui.components.ConfirmDialog
import com.aravind.habitmaker.ui.components.DayPicker
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.TimeDialog
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

/** Every block of the routine; tap to edit, + to add. */
@Composable
fun RoutineEditScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val blocks by remember { context.app.db.routine().observeBlocks() }.collectAsStateWithLifecycle(emptyList())
    var confirmReset by remember { mutableStateOf(false) }

    if (confirmReset) ConfirmDialog(
        "Remove Exam Prep?", "Your routine, alarms and done / missed history will be deleted.", "Remove",
        { scope.launch { RoutineActions.reset(context); onBack() } }, { confirmReset = false },
    )

    val sorted = blocks.sortedWith(compareBy<RoutineBlock> { it.kind == BlockType.SLEEP }.thenBy { it.start })
    ScrollScreen("Edit routine", onBack = onBack, actions = {
        IconButton({ go(Routes.routineBlock()) }) { Icon(Icons.Filled.Add, "Add block", tint = c.foreground) }
    }) {
        Text("Tap a block to change its time, days or reminder.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        AppCard(padding = 0.dp) {
            sorted.forEachIndexed { i, b ->
                if (i > 0) com.aravind.habitmaker.ui.components.Divider()
                com.aravind.habitmaker.ui.components.MenuRow(
                    b.kind.icon(), b.title, { go(Routes.routineBlock(b.id)) },
                    tint = null,
                    value = "${formatClock(b.start)}–${formatClock(b.end)}",
                    trailing = { if (!b.remind) { Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.NotificationsOff, "No reminder", tint = c.faint) } },
                )
                if (b.days != ALL_DAYS) Text("   ${daysLabel(b.days)}", style = MaterialTheme.typography.bodySmall, color = c.faint)
            }
        }
        AppButton("Add a block", { go(Routes.routineBlock()) }, kind = ButtonKind.SECONDARY, icon = Icons.Filled.Add)
        AppButton("Rebuild with new answers", { go(Routes.PREP_SETUP) }, kind = ButtonKind.GHOST)
        AppButton("Remove Exam Prep", { confirmReset = true }, kind = ButtonKind.GHOST)
    }
}

/** Add ([id] = 0) or edit one block. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockEditScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<RoutineBlock?>(null) }
    var type by remember { mutableStateOf(BlockType.STUDY) }
    var title by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(18 * 60) }
    var end by remember { mutableIntStateOf(18 * 60 + 45) }
    var days by remember { mutableIntStateOf(ALL_DAYS) }
    var remind by remember { mutableStateOf(true) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) context.app.db.routine().block(id)?.let { b ->
            loaded = b; type = b.kind; title = b.title; start = b.start; end = b.end; days = b.days; remind = b.remind
        }
    }
    when (pick) {
        "start" -> TimeDialog("Starts", start, { pick = null }) { m -> val len = (end - start + 1440) % 1440; start = m; end = (m + len) % 1440 }
        "end" -> TimeDialog("Ends", end, { pick = null }) { end = it }
    }
    if (confirmDelete) ConfirmDialog("Delete block?", "\"$title\" will be removed from your routine.", "Delete",
        { scope.launch { loaded?.let { RoutineActions.deleteBlock(context, it) }; onBack() } }, { confirmDelete = false })

    ScrollScreen(if (id == 0L) "New block" else "Edit block", onBack = onBack, actions = {
        if (id != 0L) IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = c.danger) }
    }) {
        Column {
            SectionLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockType.entries.forEach { t ->
                    Chip(t.label, type == t, {
                        if (title.isBlank() || BlockType.entries.any { it.label == title }) title = t.label
                        type = t
                    })
                }
            }
        }
        AppTextField(title, { title = it }, "Name", placeholder = "e.g. Paper 1 – Teaching aptitude")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.weight(1f), onClick = { pick = "start" }) {
                Text("Starts", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(formatClock(start), style = MaterialTheme.typography.headlineSmall, color = c.foreground)
            }
            AppCard(Modifier.weight(1f), onClick = { pick = "end" }) {
                Text("Ends", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(formatClock(end), style = MaterialTheme.typography.headlineSmall, color = c.foreground)
            }
        }
        val len = (end - start + 1440) % 1440
        Text(if (len == 0) "Start and end can't be the same." else "Length ${formatMinutes(len)}",
            style = MaterialTheme.typography.bodySmall, color = if (len == 0) c.danger else c.muted)
        Column {
            SectionLabel("Days")
            DayPicker(days) { days = it }
        }
        AppCard {
            ToggleRow(
                if (type == BlockType.WAKE) "Ring the alarm" else "Remind me when it starts",
                if (type == BlockType.WAKE) "Your alarm sound, vibration and flash." else if (type.isStudy) "Also asks \"Did you finish?\" at the end." else "A notification at the start time.",
                remind,
            ) { remind = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(type.icon(), type.tint(), 32.dp)
            Spacer(Modifier.width(10.dp))
            Text("${title.ifBlank { type.label }} · ${formatClock(start)}–${formatClock(end)}", style = MaterialTheme.typography.bodyMedium, color = c.foreground)
        }
        AppButton("Save", {
            if (len == 0 || days == 0) return@AppButton
            val b = (loaded ?: RoutineBlock(type = type.name, title = "", start = 0, end = 0))
                .copy(type = type.name, title = title.trim().ifEmpty { type.label }, start = start, end = end, days = days, remind = remind)
            scope.launch { RoutineActions.saveBlock(context, b); onBack() }
        }, enabled = len != 0 && days != 0)
    }
}
