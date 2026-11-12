package com.aravind.habitmaker.feature.prep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.ui.components.SkeletonScreen
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.routine.DayResult
import com.aravind.habitmaker.core.routine.DayStatus
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.routine.minutes
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.db.RoutineLog
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.Metric
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.theme.colors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Month calendar: green = completed day, red = missed day, plus streaks and study hours. */
@Composable
fun PrepCalendarScreen(onBack: () -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val loadedBlocks by remember { app.db.routine().observeBlocks() }.collectAsStateWithLifecycle(initialValue = null)
    val loadedLogs by remember { app.db.routine().observeLogsSince(0) }.collectAsStateWithLifecycle(initialValue = null)
    var month by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()

    val blocks = loadedBlocks ?: run { SkeletonScreen("Prep calendar", onBack); return }
    val logs = loadedLogs ?: run { SkeletonScreen("Prep calendar", onBack); return }

    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val statuses = dayStatuses(blocks, logs, first, last)
    val history = dayStatuses(blocks, logs, today.minusDays(365), today)
    val streak = DayResult.streak(history.values.reversed())
    val best = history.values.fold(0 to 0) { (best, run), s ->
        when (s) { DayStatus.DONE -> maxOf(best, run + 1) to run + 1; DayStatus.MISSED -> best to 0; else -> best to run }
    }.first

    val done = statuses.values.count { it == DayStatus.DONE }
    val missed = statuses.values.count { it == DayStatus.MISSED }
    // Study minutes done this month (logs of blocks that no longer exist count as one session).
    val byId = blocks.associateBy { it.id }
    val session = blocks.firstOrNull { it.kind.isStudy }?.minutes() ?: 45
    val studied = logs.filter { it.status == RoutineLog.DONE && LocalDate.ofEpochDay(it.day).let { d -> !d.isBefore(first) && !d.isAfter(last) } }
        .sumOf { l -> byId[l.blockId]?.let { if (it.kind.isStudy) it.minutes() else 0 } ?: session }

    ScrollScreen("Prep calendar", onBack = onBack) {
        AppCard {
            Row {
                Metric("$streak", "Current streak", Modifier.weight(1f), c.success)
                Metric("$best", "Best streak", Modifier.weight(1f))
                Metric(if (done + missed == 0) "—" else "${done * 100 / (done + missed)}%", "Days completed", Modifier.weight(1f))
            }
        }
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = c.foreground) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium, color = c.foreground,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton({ month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = if (month < YearMonth.now()) c.foreground else c.faint)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Dates.SHORT_DAYS.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = c.faint, textAlign = TextAlign.Center) }
            }
            Spacer(Modifier.height(6.dp))
            val lead = first.dayOfWeek.value - 1
            val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).forEach { i ->
                        val d = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (d != null) DayCell(d, statuses[d] ?: DayStatus.NONE, d == today)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(c.success, "Completed")
                Legend(c.danger, "Missed")
                Legend(c.info, "Today")
            }
        }
        AppCard {
            Row {
                Metric("$done", "Completed days", Modifier.weight(1f), c.success)
                Metric("$missed", "Missed days", Modifier.weight(1f), c.danger)
                Metric(formatMinutes(studied), "Studied", Modifier.weight(1f))
            }
        }
        Text("A study session is done only when you finish its focus timer. A day counts as completed when at least ${(DayResult.DONE_SHARE * 100).toInt()}% of its study sessions are done.",
            style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@Composable
private fun DayCell(d: LocalDate, s: DayStatus, isToday: Boolean) {
    val c = MaterialTheme.colors
    val (bg, fg) = when (s) {
        DayStatus.DONE -> c.success to Color.White
        DayStatus.MISSED -> c.dangerSoft to c.danger
        else -> Color.Transparent to (if (d.isAfter(LocalDate.now())) c.faint else c.foreground)
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small).background(bg)
            .then(if (isToday) Modifier.border(2.dp, c.info, MaterialTheme.shapes.small) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    val c = MaterialTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}
