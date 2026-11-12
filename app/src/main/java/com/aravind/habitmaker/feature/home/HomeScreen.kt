package com.aravind.habitmaker.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.core.util.formatTimer
import com.aravind.habitmaker.feature.habits.HabitActions
import com.aravind.habitmaker.feature.habits.HabitRow
import com.aravind.habitmaker.feature.habits.dueOn
import com.aravind.habitmaker.feature.habits.streak
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.Avatar
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.EmptyState
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.ProgressBar
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.Blue
import com.aravind.habitmaker.ui.theme.Emerald
import com.aravind.habitmaker.ui.theme.Violet
import com.aravind.habitmaker.ui.theme.colors
import com.aravind.habitmaker.ui.components.SkeletonScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.ui.text.font.FontWeight
import com.aravind.habitmaker.ui.components.AnimatedNumber
import com.aravind.habitmaker.ui.components.bouncyClickable
import androidx.compose.material3.TextButton
import com.aravind.habitmaker.ui.components.rememberBreathingGlow
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.feature.prep.current
import com.aravind.habitmaker.feature.prep.icon
import com.aravind.habitmaker.feature.prep.next
import com.aravind.habitmaker.feature.prep.on
import com.aravind.habitmaker.feature.prep.tint

/** Today at a glance: screen time, focus, unlocks and today's habits. */
@Composable
internal fun HomeScreen(go: (String) -> Unit, state: HomeState) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    val loadedUser by state.user.collectAsStateWithLifecycle()
    val loadedSettings by state.settings.collectAsStateWithLifecycle()
    val loadedData by state.data.collectAsStateWithLifecycle()
    val run by FocusTimer.run.collectAsStateWithLifecycle()

    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(state) {
        resumes++
        state.refreshUsage()
        onPauseOrDispose {}
    }
    val usage by state.usage.collectAsStateWithLifecycle()
    val hasAccess = remember(resumes) { app.usage.hasAccess() }

    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }

    // Keep the logo visible until real values replace the initial loading state.
    val user = loadedUser ?: run { SkeletonScreen("Habit Maker"); return }
    val s = loadedSettings ?: run { SkeletonScreen("Habit Maker"); return }
    val (habits, logs, focus, plan, routine) = loadedData ?: run { SkeletonScreen("Habit Maker"); return }
    if (usage == null) { SkeletonScreen("Habit Maker"); return }

    val due = habits.filter { it.dueOn(today) }
    val todayLogs = logs.filter { it.day == today.toEpochDay() }.associateBy { it.habitId }
    val doneCount = due.count { (todayLogs[it.id]?.count ?: 0) >= it.target }
    val focusMin = focus.sumOf { it.actualMin }
    val bestStreak = habits.maxOfOrNull { it.streak(logs, today).current } ?: 0

    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val flameAlpha = rememberBreathingGlow(minAlpha = 0.7f, maxAlpha = 1f)
    val shieldAlpha = rememberBreathingGlow(minAlpha = 0.65f, maxAlpha = 1f, durationMs = 1500)

    ScrollScreen("Habit Maker", actions = { Avatar(user.initials, 36.dp) { go(Routes.PROFILE) }; Spacer(Modifier.width(12.dp)) }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    if (user.name.isNotBlank()) "$greeting, ${user.name.substringBefore(' ')}" else greeting,
                    style = MaterialTheme.typography.headlineSmall, color = c.foreground,
                )
                val onTrackPercent = if (due.isNotEmpty()) ((doneCount.toFloat() / due.size) * 100).toInt() else 100
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c.primary))
                    Spacer(Modifier.width(6.dp))
                    Text("$onTrackPercent% on track today", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            if (bestStreak > 0) {
                Row(
                    Modifier
                        .graphicsLayer { alpha = flameAlpha }
                        .clip(RoundedCornerShape(50))
                        .background(c.warningSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = c.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${bestStreak}d", style = MaterialTheme.typography.labelMedium, color = c.warning, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Hero Overview: Visual Dual Gauge & Analytics (from home_dashboard redesign)
        val focusTargetMin = (s.defaultFocusMin * 4).coerceAtLeast(60)
        val screenTargetMs = s.dailyTargetMin * 60_000L
        val screenUsedMs = usage?.totalMs ?: 0L
        val focusFraction = if (focusTargetMin > 0) focusMin.toFloat() / focusTargetMin else 0f
        val screenFraction = if (screenTargetMs > 0) screenUsedMs.toFloat() / screenTargetMs else 0f

        AppCard(elevated = true, padding = 16.dp) {
            if (hasAccess) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DualRingGauge(
                        focusFraction = focusFraction,
                        screenFraction = screenFraction,
                        focusText = formatMinutes(focusMin),
                        modifier = Modifier.size(116.dp),
                    )

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Metric 1: Focus Hours
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.primary))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Focus Hours", style = MaterialTheme.typography.labelMedium, color = c.foreground)
                                }
                                Text("${String.format("%.1f", focusMin / 60f)} / ${String.format("%.1f", focusTargetMin / 60f)}h", style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(focusFraction, color = c.primary, trackColor = c.sunken)
                        }

                        // Metric 2: Screen Limit
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.secondary))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Screen Limit", style = MaterialTheme.typography.labelMedium, color = c.foreground)
                                }
                                Text("${formatMinutes((screenUsedMs / 60000L).toInt())} / ${formatMinutes(s.dailyTargetMin)}", style = MaterialTheme.typography.labelSmall, color = c.secondary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(screenFraction, color = if (screenUsedMs > screenTargetMs) c.danger else c.secondary, trackColor = c.sunken)
                        }

                        // Quick Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                            Row(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(c.surfaceElevated).padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, tint = c.primary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("$doneCount of ${due.size} Habits", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.foreground)
                            }
                            if (bestStreak > 0) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(6.dp)).background(c.surfaceElevated).padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Bolt, null, tint = c.warning, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${bestStreak * 15} XP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.warning, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("—", style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                Spacer(Modifier.height(6.dp))
                Text("Allow usage access to see your screen time metrics.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(12.dp))
                AppButton("Allow Access", { go(Routes.PERMISSIONS) }, kind = ButtonKind.SECONDARY, fullWidth = false)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Focus Time", formatMinutes(focusMin), Icons.Filled.Timer, Violet, Modifier.weight(1f)) { go(Routes.FOCUS) }
            StatTile("Unlocks", usage?.unlocks?.takeIf { it >= 0 }?.toString() ?: "—", Icons.Filled.LockOpen, Blue, Modifier.weight(1f)) { go(Routes.STATS) }
            StatTile("Habits Done", "$doneCount/${due.size}", Icons.Filled.CheckCircle, Emerald, Modifier.weight(1f)) { go(Routes.HABITS) }
        }

        if (s.blockNowUntil > now) {
            AppCard(onClick = { go(Routes.BLOCK) }, elevated = true, borderColor = c.danger.copy(alpha = 0.4f), padding = 12.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c.dangerSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.graphicsLayer { alpha = shieldAlpha }) {
                            Icon(Icons.Filled.Shield, null, tint = c.danger, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("App Shield Guard Active", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text("${formatTimer((s.blockNowUntil - now) / 1000)} remaining · Distractions locked", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    AppButton("Manage", { go(Routes.BLOCK) }, fullWidth = false, kind = ButtonKind.SECONDARY)
                }
            }
        }

        // Exam prep: what's on now
        val p = plan
        if (p != null) {
            val todays = routine.on(today)
            val minute = LocalTime.now().let { it.hour * 60 + it.minute }
            val cur = todays.current(minute)
            val nxt = todays.next(minute)
            val days = p.examDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }

            AppCard(onClick = { go(Routes.PREP) }, elevated = true, padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.School, null, tint = c.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("EXAM SPRINT", style = MaterialTheme.typography.labelSmall, color = c.secondary, fontWeight = FontWeight.Bold)
                    }
                    if (days != null && days >= 0) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(c.secondary.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("${p.exam} in ${days}d", style = MaterialTheme.typography.labelSmall, color = c.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cur?.title ?: "Revision Session", style = MaterialTheme.typography.titleMedium, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Filled.Schedule, null, tint = c.warning, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                cur?.let { "${formatClock(it.start)} - ${formatClock(it.end)}" } ?: (nxt?.let { "Next: ${formatClock(it.start)} · ${it.title}" } ?: "Scheduled routine block"),
                                style = MaterialTheme.typography.bodySmall, color = c.muted,
                            )
                        }
                    }
                    AppButton("Start", { go(Routes.PREP) }, fullWidth = false, kind = ButtonKind.PRIMARY, icon = Icons.Filled.PlayArrow)
                }
            }
        }

        val r = run
        if (r != null) {
            AppCard(onClick = { go(Routes.FOCUS) }, elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Timer, c.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text(if (r.paused) "Paused" else "In progress", style = MaterialTheme.typography.bodySmall, color = if (r.paused) c.muted else c.primary)
                    }
                    Text(formatTimer(r.remainingMs(now) / 1000), style = MaterialTheme.typography.titleLarge, color = c.foreground, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            AppCard(
                onClick = { FocusTimer.start(context, s.defaultFocusMin, "Focus"); go(Routes.FOCUS) },
                elevated = true,
                borderColor = c.primary.copy(alpha = 0.35f),
                padding = 14.dp,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.primarySoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Bolt, null, tint = c.primary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Instant ${s.defaultFocusMin}m Focus Sprint", style = MaterialTheme.typography.titleSmall, color = c.foreground, fontWeight = FontWeight.SemiBold)
                        Text("App blocker engages automatically", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.primary, modifier = Modifier.size(20.dp))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY'S HABITS", style = MaterialTheme.typography.labelSmall, color = c.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${doneCount}/${due.size} Ready", style = MaterialTheme.typography.labelSmall, color = c.primary)
                Spacer(Modifier.width(8.dp))
                TextButton({ go(Routes.HABITS) }) { Text("View all", color = c.primary) }
            }
            if (due.isEmpty()) {
                AppCard {
                    EmptyState(Icons.Filled.CheckCircle, if (habits.isEmpty()) "No habits yet" else "Nothing due today",
                        if (habits.isEmpty()) "Add your first habit to start a streak." else "Enjoy your day off.") {
                        AppButton("Add habit", { go(Routes.habitEdit()) }, kind = ButtonKind.SECONDARY, fullWidth = false, icon = Icons.Filled.Add)
                    }
                }
            } else {
                due.forEach { h ->
                    val count = todayLogs[h.id]?.count ?: 0
                    HabitRow(
                        h, count,
                        subtitle = if (h.target > 1) "$count/${h.target} today" else if (count >= h.target) "Done" else "To do",
                        onCheck = { scope.launch { HabitActions.tap(context, h) } },
                        onOpen = { go(Routes.habitDetail(h.id)) },
                        streak = h.streak(logs, today).current,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colors
    AppCard(modifier = modifier, onClick = onClick, padding = 14.dp) {
        IconTile(icon, tint, 34.dp)
        Spacer(Modifier.height(8.dp))
        AnimatedNumber(value) { text ->
            Text(text, style = MaterialTheme.typography.titleMedium, color = c.foreground, fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

/** Dual Concentric Progress Gauge (Outer: Focus Hours, Inner: Screen Limit) from Serene Focus. */
@Composable
fun DualRingGauge(
    focusFraction: Float,
    screenFraction: Float,
    focusText: String,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colors
    val animFocus by animateFloatAsState(
        targetValue = focusFraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "gauge_focus",
    )
    val animScreen by animateFloatAsState(
        targetValue = screenFraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "gauge_screen",
    )

    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeOuter = 7.dp.toPx()
            val strokeInner = 6.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer ring (Focus - Emerald)
            val radiusOuter = (size.minDimension - strokeOuter) / 2f - 3.dp.toPx()
            drawCircle(
                color = c.border.copy(alpha = 0.35f),
                radius = radiusOuter,
                center = center,
                style = Stroke(strokeOuter),
            )
            drawArc(
                color = c.primary,
                startAngle = -90f,
                sweepAngle = 360f * animFocus,
                useCenter = false,
                topLeft = Offset(center.x - radiusOuter, center.y - radiusOuter),
                size = Size(radiusOuter * 2, radiusOuter * 2),
                style = Stroke(strokeOuter, cap = StrokeCap.Round),
            )

            // Inner ring (Screen Time - Indigo/Lavender)
            val radiusInner = radiusOuter - strokeOuter - 5.dp.toPx()
            drawCircle(
                color = c.border.copy(alpha = 0.25f),
                radius = radiusInner,
                center = center,
                style = Stroke(strokeInner),
            )
            drawArc(
                color = c.secondary,
                startAngle = -90f,
                sweepAngle = 360f * animScreen,
                useCenter = false,
                topLeft = Offset(center.x - radiusInner, center.y - radiusInner),
                size = Size(radiusInner * 2, radiusInner * 2),
                style = Stroke(strokeInner, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DEEP WORK", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = c.muted, fontWeight = FontWeight.Bold)
            Text(focusText, style = MaterialTheme.typography.titleMedium, color = c.primary, fontWeight = FontWeight.Bold)
        }
    }
}
