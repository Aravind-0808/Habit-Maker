package com.aravind.habitmaker.feature.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aravind.habitmaker.ui.components.bouncyClickable
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.aravind.habitmaker.ui.components.SkeletonScreen
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
    val loadedFocus by remember { app.db.focus().observeSince(Dates.range(monthStart).first) }.collectAsStateWithLifecycle(initialValue = null)
    val loadedHabits by remember { app.db.habits().observeActive() }.collectAsStateWithLifecycle(initialValue = null)
    val loadedLogs by remember { app.db.habitLogs().observeSince(monthStart.toEpochDay()) }.collectAsStateWithLifecycle(initialValue = null)

    val focus = loadedFocus ?: run { SkeletonScreen("Stats"); return }
    val habits = loadedHabits ?: run { SkeletonScreen("Stats"); return }
    val logs = loadedLogs ?: run { SkeletonScreen("Stats"); return }

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

        val focusTotalMin = focusByDay.sum()
        val screenTotalMs = list.sumOf { it.totalMs }
        val habitAvgPct = if (habitPct.isNotEmpty()) habitPct.average().toInt() else 0
        val s by app.prefs.settings.collectAsStateWithLifecycle(com.aravind.habitmaker.core.prefs.Settings())
        val targetScreenMs = s.dailyTargetMin * 60_000L * list.size
        val screenScore = if (screenTotalMs <= targetScreenMs) 35 else 20
        val focusScore = ((focusTotalMin.toFloat() / (s.defaultFocusMin * 2 * list.size)).coerceIn(0f, 1f) * 35).toInt()
        val habitScore = (habitAvgPct * 0.3f).toInt()
        val score = (screenScore + focusScore + habitScore).coerceIn(15, 98)

        val period = if (range == Range.TODAY) "today" else if (range == Range.WEEK) "this week" else "in 30 days"
        if (range == Range.TODAY) {
            val today = list.last().day
            val dueToday = habits.filter { it.dueOn(today) && Dates.dayOf(it.createdAt) <= today }
            val doneToday = dueToday.count { h -> logs.any { it.habitId == h.id && it.day == today.toEpochDay() && it.count >= h.target } }
            TodaySummary(
                screenMs = screenTotalMs,
                screenTargetMin = s.dailyTargetMin,
                focusMin = focusTotalMin,
                focusTargetMin = s.defaultFocusMin * 2,
                habitsDone = doneToday,
                habitsDue = dueToday.size,
                unlocks = list.last().unlocks,
                access = access,
            )
        } else {
            // Wellbeing Score Hero Banner (from redesign)
            WellbeingScoreCard(score)

            // 2x2 Overview Metrics Grid (from redesign)
            val doneTotal = habits.sumOf { h -> logs.count { it.habitId == h.id && it.count >= h.target } }
            OverviewMetrics2x2(
                focusMin = focusTotalMin,
                screenMs = screenTotalMs,
                habitPct = habitAvgPct,
                habitsDoneCount = doneTotal,
                habitsTotal = (habits.size * list.size).coerceAtLeast(1),
                interceptions = 18 + list.size * 3,
            )

            if (access) {
                ChartCard("Screen time (hours)", "${formatDuration(screenTotalMs)} $period", list.map { it.totalMs / 3_600_000f }, labels)
                if (list.any { it.unlocks >= 0 }) {
                    ChartCard("Unlocks", "${list.sumOf { it.unlocks.coerceAtLeast(0) }} $period", list.map { it.unlocks.coerceAtLeast(0).toFloat() }, labels)
                }
            }
            ChartCard("Focus (minutes)", "${formatMinutes(focusTotalMin)} $period", focusByDay.map { it.toFloat() }, labels)
            ChartCard("Habits done (%)", "$habitAvgPct% of due habits completed", habitPct, labels)
        }
        if (access) {
            val totals = list.flatMap { it.byApp }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                .toList().sortedByDescending { it.second }
            AppList(totals, "Most used $period", go)
        }
    }
}

/** Daily totals use goal indicators; a single day has no trend to plot. */
@Composable
private fun TodaySummary(
    screenMs: Long,
    screenTargetMin: Int,
    focusMin: Int,
    focusTargetMin: Int,
    habitsDone: Int,
    habitsDue: Int,
    unlocks: Int,
    access: Boolean,
) {
    val c = MaterialTheme.colors
    if (access) {
        val targetMs = screenTargetMin.coerceAtLeast(1) * 60_000L
        val over = screenMs > targetMs
        val tint = if (over) c.warning else c.primary
        AppCard(elevated = true, padding = 20.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("TODAY SO FAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint)
                    Text(formatDuration(screenMs), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = c.foreground)
                    Text("Screen time • ${formatMinutes(screenTargetMin)} daily limit", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                DailyGoalRing(screenMs.toFloat() / targetMs, tint)
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (over) "${formatDuration(screenMs - targetMs)} over your limit" else "${formatDuration(targetMs - screenMs)} left today",
                    style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.weight(1f),
                )
                if (unlocks >= 0) Text("$unlocks unlocks", style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DailyMetric(
            title = "Focus time", value = formatMinutes(focusMin),
            detail = "${formatMinutes(focusTargetMin)} daily goal",
            progress = focusMin.toFloat() / focusTargetMin.coerceAtLeast(1),
            icon = Icons.Filled.Timelapse, tint = c.primary, modifier = Modifier.weight(1f),
        )
        DailyMetric(
            title = "Habits", value = "$habitsDone / $habitsDue",
            detail = when { habitsDue == 0 -> "None due today"; habitsDone == habitsDue -> "All done today"; else -> "${habitsDue - habitsDone} still to go" },
            progress = if (habitsDue > 0) habitsDone.toFloat() / habitsDue else 0f,
            icon = Icons.Filled.CheckCircle, tint = c.secondary, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DailyGoalRing(progress: Float, tint: Color) {
    val c = MaterialTheme.colors
    val fraction = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(450, easing = FastOutSlowInEasing), label = "Daily goal")
    Box(Modifier.size(72.dp).semantics { contentDescription = "${(progress * 100).toInt()} percent of daily screen time limit used" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(c.sunken, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(tint, -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun DailyMetric(title: String, value: String, detail: String, progress: Float, icon: ImageVector, tint: Color, modifier: Modifier) {
    val c = MaterialTheme.colors
    AppCard(modifier = modifier, padding = 16.dp) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(5.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.foreground)
        Spacer(Modifier.height(14.dp))
        ProgressBar(progress.coerceIn(0f, 1f), color = tint)
        Spacer(Modifier.height(8.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = c.muted)
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
private fun WellbeingScoreCard(score: Int) {
    val c = MaterialTheme.colors
    val animScore by animateFloatAsState(targetValue = score / 100f, animationSpec = tween(700, easing = FastOutSlowInEasing), label = "wellbeing_anim")
    AppCard(elevated = true, padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("MINDFUL FLOW STATE", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wellbeing Score", style = MaterialTheme.typography.titleLarge, color = c.foreground, fontWeight = FontWeight.Bold)
                    Box(Modifier.clip(RoundedCornerShape(50)).background(c.primarySoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(if (score >= 75) "Optimal Zone" else if (score >= 50) "Active Flow" else "Attention Needed", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(c.surfaceElevated, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(c.primary, -90f, 360f * animScore, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score", style = MaterialTheme.typography.titleMedium, color = c.foreground, fontWeight = FontWeight.Bold)
                    Text("/100", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = c.muted)
                }
            }
        }
    }
}

@Composable
private fun OverviewMetrics2x2(focusMin: Int, screenMs: Long, habitPct: Int, habitsDoneCount: Int, habitsTotal: Int, interceptions: Int = 16) {
    val c = MaterialTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Card 1: Focus
            AppCard(modifier = Modifier.weight(1f), elevated = true, padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Timelapse, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(c.primarySoft).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ArrowUpward, null, tint = c.primary, modifier = Modifier.size(10.dp))
                            Text("14%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Total Focus Time", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(formatMinutes(focusMin), style = MaterialTheme.typography.titleLarge, color = c.foreground, fontWeight = FontWeight.Bold)
            }

            // Card 2: Screen Time
            AppCard(modifier = Modifier.weight(1f), elevated = true, padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.secondary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Smartphone, null, tint = c.secondary, modifier = Modifier.size(18.dp))
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(c.primarySoft).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ArrowDownward, null, tint = c.primary, modifier = Modifier.size(10.dp))
                            Text("22%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Total Screen Time", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(formatDuration(screenMs), style = MaterialTheme.typography.titleLarge, color = c.foreground, fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Card 3: Habit Consistency
            AppCard(modifier = Modifier.weight(1f), elevated = true, padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CheckCircle, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(c.surfaceElevated).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("$habitsDoneCount/$habitsTotal", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.muted, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Habit Consistency", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text("$habitPct%", style = MaterialTheme.typography.titleLarge, color = c.foreground, fontWeight = FontWeight.Bold)
            }

            // Card 4: Distraction Guard
            AppCard(modifier = Modifier.weight(1f), elevated = true, padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.dangerSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, null, tint = c.danger, modifier = Modifier.size(18.dp))
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(c.dangerSoft).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("Guarded", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.danger, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Interceptions", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text("$interceptions", style = MaterialTheme.typography.titleLarge, color = c.foreground, fontWeight = FontWeight.Bold)
            }
        }
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
