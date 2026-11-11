package com.aravind.habitmaker.feature.blocking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.blocking.BlockerStatus
import com.aravind.habitmaker.core.permissions.Perm
import com.aravind.habitmaker.core.permissions.Permissions
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.util.daysLabel
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.formatTimer
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.Chip
import com.aravind.habitmaker.ui.components.Divider
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Block hub: "block now", and links to every blocking setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
    val rules by remember { app.db.appRules().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val sites by remember { app.db.sites().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val schedules by remember { app.db.schedules().observeAll() }.collectAsStateWithLifecycle(emptyList())

    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose {} }
    val serviceOn = remember(resumes) { Permissions.accessibilityOn(context) }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }

    var blockMin by remember { mutableIntStateOf(30) }
    var strict by remember { mutableStateOf(false) }
    val distracting = rules.count { it.distracting || it.shortsOnly }
    val limited = rules.count { it.dailyLimitMin > 0 }
    val active = s.blockNowUntil > now

    val status by BlockerStatus.state.collectAsStateWithLifecycle()

    ScrollScreen("Block") {
        if (!serviceOn) {
            InlineBanner("Blocking is off. Turn on the Habit Maker accessibility service so blocks can work.", Tone.WARNING)
            AppButton("Turn on blocking", { go(Routes.PERMISSIONS) }, kind = ButtonKind.SECONDARY)
        } else if (!status.running) {
            InlineBanner(
                "Android stopped the blocker (after an app update, or the phone closed the app). Open Accessibility → Habit Maker blocker → turn it OFF, then ON again." +
                    if (Permissions.isXiaomi) " On Xiaomi also allow Autostart and battery \"No restrictions\" so it stays on." else "",
                Tone.WARNING,
            )
            AppButton("Open Accessibility settings", {
                runCatching { context.startActivity(Permissions.settingsIntent(context, Perm.ACCESSIBILITY)) }
            }, kind = ButtonKind.SECONDARY)
            if (Permissions.isXiaomi) AppButton("Xiaomi Autostart", {
                runCatching { context.startActivity(Permissions.settingsIntent(context, Perm.AUTOSTART)) }
                    .onFailure { go(Routes.PERMISSIONS) }
            }, kind = ButtonKind.GHOST)
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Shield, if (active) c.danger else c.foreground)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Block now", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    Text(
                        if (distracting == 0) "Pick your distracting apps first" else "Blocks your $distracting distracting apps",
                        style = MaterialTheme.typography.bodySmall, color = c.muted,
                    )
                }
                if (active) Badge(formatTimer((s.blockNowUntil - now) / 1000), Tone.DANGER)
            }
            Spacer(Modifier.height(12.dp))
            if (active) {
                if (s.strict) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = c.warning, modifier = Modifier.padding(end = 6.dp))
                        Text("Strict mode — can't be stopped early.", style = MaterialTheme.typography.bodySmall, color = c.warning)
                    }
                } else {
                    AppButton("Stop blocking", { scope.launch { app.prefs.update { it.copy(blockNowUntil = 0) } } }, kind = ButtonKind.SECONDARY)
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120, 240).forEach { m -> Chip(if (m < 60) "$m min" else "${m / 60} h", blockMin == m, { blockMin = m }) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Strict (can't stop early)", style = MaterialTheme.typography.bodyMedium, color = c.foreground, modifier = Modifier.weight(1f))
                    Switch(strict, { strict = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
                }
                Spacer(Modifier.height(8.dp))
                AppButton("Start blocking", {
                    scope.launch { app.prefs.update { it.copy(blockNowUntil = System.currentTimeMillis() + blockMin * 60_000L, strict = strict) } }
                }, enabled = distracting > 0)
            }
        }

        Column {
            SectionLabel("Rules")
            AppCard(padding = 0.dp) {
                NavRow(Icons.Filled.Apps, "Distracting apps", "$distracting selected") { go(Routes.BLOCK_APPS) }
                Divider()
                NavRow(Icons.Filled.HourglassBottom, "App limits", if (limited == 0) "No limits" else "$limited apps") { go(Routes.BLOCK_LIMITS) }
                Divider()
                NavRow(Icons.Filled.Schedule, "Schedules", schedules.count { it.enabled }.let { n -> if (schedules.isEmpty()) "None" else "$n of ${schedules.size} on" }) { go(Routes.BLOCK_SCHEDULE) }
                Divider()
                NavRow(Icons.Filled.Language, "Websites", if (!s.blockWebsites) "Off" else "${sites.size} blocked") { go(Routes.BLOCK_SITES) }
            }
        }

        Column {
            SectionLabel("Blocker status")
            AppCard {
                StatusLine("Service", if (status.running) "Running" else "Not running", if (status.running) Tone.SUCCESS else Tone.DANGER)
                Spacer(Modifier.height(8.dp))
                StatusLine("Focus blocking", if (s.blockDuringFocus) "On" else "Off", if (s.blockDuringFocus) Tone.SUCCESS else Tone.NEUTRAL)
                Spacer(Modifier.height(8.dp))
                StatusLine("Distracting apps", "$distracting", if (distracting > 0) Tone.SUCCESS else Tone.WARNING)
                Spacer(Modifier.height(8.dp))
                Text("Last app seen: ${status.lastApp?.let { app.apps.label(it) } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text("Last block: ${status.lastBlock?.let { b -> app.apps.label(b.substringBefore(" · ")) + " · " + b.substringAfter(" · ") } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }

        Column {
            SectionLabel("Short videos")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.SmartDisplay, c.danger)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Always block Shorts & Reels", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text("All day, not only while studying. To block them only during study, use Distracting apps → \"Shorts only\".", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Switch(s.blockShorts, { on -> scope.launch { app.prefs.update { it.copy(blockShorts = on) } } },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, tone: Tone) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colors.foreground, modifier = Modifier.weight(1f))
        Badge(value, tone)
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    val c = MaterialTheme.colors
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        com.aravind.habitmaker.ui.components.MenuRow(icon, title, onClick, value = value, trailing = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.faint)
        })
    }
}
