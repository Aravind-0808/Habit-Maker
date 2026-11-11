package com.aravind.habitmaker.feature.stats

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.usage.DayUsage
import com.aravind.habitmaker.feature.habits.dueOn
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppIcon
import com.aravind.habitmaker.ui.components.BarChart
import com.aravind.habitmaker.ui.components.EmptyState
import com.aravind.habitmaker.ui.components.LoadingState
import com.aravind.habitmaker.ui.components.Metric
import com.aravind.habitmaker.ui.components.ProgressBar
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.Segmented
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.theme.colors
import java.time.LocalDate

private enum class Range(val label: String, val days: Int) { TODAY("Today", 1), WEEK("7 days", 7), MONTH("30 days", 30) }

/** Screen time, unlocks, focus and habits for today, the last 7 days or the last 30 days. */
@Composable
fun StatsScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    var range by remember { mutableStateOf(Range.TODAY) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose {} }
    val access = remember(resumes) { app.usage.hasAccess() }
    // The last 7 days first (fast), then all 30; the ranges are slices of it.
    val month by produceState<List<DayUsage>?>(null, resumes) {
        value = app.usage.lastDays(7)
        value = app.usage.lastDays(30)
    }

    val monthStart = remember { LocalDate.now().minusDays(29) }
    val focus by remember { app.db.focus().observeSince(Dates.range(monthStart).first) }.collectAsStateWithLifecycle(emptyList())
    val habits by remember { app.db.habits().observeActive() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember { app.db.habitLogs().observeSince(monthStart.toEpochDay()) }.collectAsStateWithLifecycle(emptyList())

    val list = month?.takeLast(range.days)
    val labels = list?.mapIndexed { i, d ->
        if (range == Range.MONTH) (if (i % 5 == 4 || i == list.lastIndex) "${d.day.dayOfMonth}" else "")
        else Dates.SHORT_DAYS[d.day.dayOfWeek.value - 1]
    } ?: emptyList()
    val focusByDay = list?.map { d -> focus.filter { Dates.dayOf(it.start) == d.day }.sumOf { it.actualMin } } ?: emptyList()
    val habitPct = list?.map { d ->
        val due = habits.filter { it.dueOn(d.day) && Dates.dayOf(it.createdAt) <= d.day }
        if (due.isEmpty()) 0f else due.count { h -> logs.any { it.habitId == h.id && it.day == d.day.toEpochDay() && it.count >= h.target } } * 100f / due.size
    } ?: emptyList()

    ScrollScreen("Stats", actions = {
        if (list != null && access && range != Range.TODAY) IconButton({ share(context, list, focusByDay, habitPct) }) { Icon(Icons.Filled.Share, "Share report", tint = c.foreground) }
    }) {
        Segmented(Range.entries.map { it to it.label }, range, { range = it })

        if (!access) {
            AppCard {
                EmptyState(Icons.Filled.BarChart, "Usage access needed", "Allow usage access to see screen time and unlocks.") {
                    AppButton("Allow", { go(Routes.PERMISSIONS) }, fullWidth = false)
                }
            }
        }
        if (list == null) { LoadingState(); return@ScrollScreen }

        if (range == Range.TODAY) {
            val today = list.last()
            if (access) {
                AppCard {
                    Row {
                        Metric(formatDuration(today.totalMs), "Screen time", Modifier.weight(1f))
                        Metric(if (today.unlocks >= 0) "${today.unlocks}" else "—", "Unlocks", Modifier.weight(1f))
                        Metric(formatMinutes(focusByDay.last()), "Focus", Modifier.weight(1f))
                    }
                }
                AppList(today.byApp, "Most used today", go)
            } else {
                AppCard { Metric(formatMinutes(focusByDay.last()), "Focus today") }
            }
            return@ScrollScreen
        }

        val n = list.size
        val period = if (range == Range.WEEK) "this week" else "in 30 days"
        if (access) {
            val total = list.sumOf { it.totalMs }
            val used = list.filter { it.totalMs > 0 }
            AppCard {
                Row {
                    Metric(formatDuration(total / n), "Daily average", Modifier.weight(1f))
                    Metric(used.minByOrNull { it.totalMs }?.let { formatDuration(it.totalMs) } ?: "—", "Best day", Modifier.weight(1f))
                    Metric(used.maxByOrNull { it.totalMs }?.let { formatDuration(it.totalMs) } ?: "—", "Heaviest day", Modifier.weight(1f))
                }
                if (range == Range.MONTH && n == 30) {
                    val first = list.take(15).sumOf { it.totalMs }
                    val last = list.takeLast(15).sumOf { it.totalMs }
                    if (first > 0) {
                        val change = ((last - first) * 100 / first).toInt()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (change <= 0) "Screen time down ${-change}% vs the first half of the month. Nice!" else "Screen time up $change% vs the first half of the month.",
                            style = MaterialTheme.typography.bodySmall, color = if (change <= 0) c.success else c.warning,
                        )
                    }
                }
            }
            ChartCard("Screen time (hours)", "${formatDuration(total)} $period", list.map { it.totalMs / 3_600_000f }, labels)
            if (list.any { it.unlocks >= 0 }) {
                ChartCard("Unlocks", "${list.sumOf { it.unlocks.coerceAtLeast(0) }} $period", list.map { it.unlocks.coerceAtLeast(0).toFloat() }, labels)
            }
        }
        ChartCard("Focus (minutes)", "${formatMinutes(focusByDay.sum())} $period", focusByDay.map { it.toFloat() }, labels)
        ChartCard("Habits done (%)", "${habitPct.average().toInt()}% of due habits completed", habitPct, labels)
        if (access) {
            val totals = list.flatMap { it.byApp }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                .toList().sortedByDescending { it.second }
            AppList(totals, "Most used $period", go)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, values: List<Float>, labels: List<String>) {
    val c = MaterialTheme.colors
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.foreground)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(12.dp))
        BarChart(values, labels)
    }
}

@Composable
private fun AppList(byApp: List<Pair<String, Long>>, title: String, go: (String) -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val top = byApp.filter { it.second >= 60_000 }.take(15)
    val max = top.firstOrNull()?.second?.toFloat() ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        if (top.isEmpty()) {
            AppCard { EmptyState(Icons.Filled.BarChart, "Nothing yet", "Apps you use will show up here.") }
        }
        AppCard(padding = 0.dp) {
            top.forEach { (pkg, ms) ->
                Row(
                    Modifier.fillMaxWidth().clickable { go(Routes.appUsage(pkg)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(pkg)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(app.apps.label(pkg), style = MaterialTheme.typography.bodyLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatDuration(ms), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(ms / max)
                    }
                }
            }
        }
    }
}

private fun share(context: android.content.Context, days: List<DayUsage>, focus: List<Int>, habitPct: List<Float>) {
    val app = context.app
    val total = days.sumOf { it.totalMs }
    val topApps = days.flatMap { it.byApp }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        .entries.sortedByDescending { it.value }.take(3)
        .joinToString("\n") { "  • ${app.apps.label(it.key)} — ${formatDuration(it.value)}" }
    val text = buildString {
        appendLine("My last ${days.size} days with Habit Maker")
        appendLine("Screen time: ${formatDuration(total)} (avg ${formatDuration(total / days.size)}/day)")
        if (days.any { it.unlocks >= 0 }) appendLine("Unlocks: ${days.sumOf { it.unlocks.coerceAtLeast(0) }}")
        appendLine("Focus: ${formatMinutes(focus.sum())}")
        appendLine("Habits done: ${habitPct.average().toInt()}% of the time")
        if (topApps.isNotEmpty()) { appendLine("Top apps:"); appendLine(topApps) }
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
}
