package com.aravind.habitmaker.feature.permissions

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.aravind.habitmaker.ui.components.AppHeader
import com.aravind.habitmaker.ui.components.ProgressBar
import com.aravind.habitmaker.ui.components.SectionLabel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.layout.Column
import com.aravind.habitmaker.core.permissions.Perm
import com.aravind.habitmaker.core.permissions.Permissions
import com.aravind.habitmaker.core.blocking.BlockerStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors

private fun Perm.icon() = when (this) {
    Perm.USAGE -> Icons.Filled.BarChart
    Perm.ACCESSIBILITY -> Icons.Filled.Accessibility
    Perm.NOTIFICATIONS -> Icons.Filled.Notifications
    Perm.EXACT_ALARM -> Icons.Filled.Alarm
    Perm.BATTERY -> Icons.Filled.BatteryChargingFull
    Perm.FULL_SCREEN -> Icons.Filled.AlarmOn
    Perm.AUTOSTART -> Icons.Filled.RestartAlt
}

/** Checklist of the special permissions, re-checked every time the page comes back. */
@Composable
fun PermissionsScreen(onBack: (() -> Unit)?, onDone: (() -> Unit)? = null) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose {} }
    val blocker by BlockerStatus.state.collectAsStateWithLifecycle()
    val perms = Permissions.visible()
    val status = remember(tick, blocker.running) { perms.associateWith { Permissions.granted(context, it) } }
    val checkable = perms.filter { Permissions.checkable(it) }
    val enabled = checkable.count { status[it] == true }
    val allRequired = perms.filter { it.required }.all { status[it] == true }

    Column(Modifier.fillMaxSize().background(c.background)) {
        AppHeader("App permissions", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Shield, c.primary, 48.dp)
                    Badge("$enabled / ${checkable.size} enabled", Tone.SUCCESS)
                }
                Text("Make room\nfor focus.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = c.onHero)
                Text("Connect the features you need. Android will ask you to approve each permission.", style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.8f))
                ProgressBar(enabled.toFloat() / checkable.size.coerceAtLeast(1), color = c.primary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("The essentials")
                Text("For screen time and app blocking", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            PermissionCards(perms.filter { it.required }, onPermissionChanged = { tick++ })

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Build a reliable routine")
                Text("Optional support for reminders, alarms and background activity", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            PermissionCards(perms.filter { !it.required }, onPermissionChanged = { tick++ })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Shield, null, tint = c.muted, modifier = Modifier.size(18.dp))
                Text("You're in control. Change permissions anytime in Android Settings or return here from Profile.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        if (onDone != null) {
            Column(
                Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppButton(if (allRequired) "Continue" else "Continue for now", onDone)
                Text(if (allRequired) "Your essential features are ready." else "You can finish later. Screen time and blocking need the essentials above.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

/** One card per permission with its status and an Allow button. Used by the Permissions page and Exam Prep setup. */
@Composable
fun PermissionCards(perms: List<Perm>, onPermissionChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose {} }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; onPermissionChanged() }
    val blocker by BlockerStatus.state.collectAsStateWithLifecycle()
    val status = remember(tick, blocker.running) { perms.associateWith { Permissions.granted(context, it) } }
    val stuck = remember(tick, blocker.running) { Permissions.accessibilityStuck(context) }

    perms.forEach { p ->
        val ok = status[p] == true
        var showHelp by rememberSaveable(p) { mutableStateOf(false) }
        AppCard(borderColor = if (ok) c.success.copy(alpha = 0.25f) else c.border, padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(p.icon(), if (ok) c.success else c.primary, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.foreground)
                    Spacer(Modifier.height(6.dp))
                    Text(p.why, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    p == Perm.ACCESSIBILITY && stuck -> Badge("On, but stopped", Tone.DANGER)
                    !Permissions.checkable(p) -> Badge("Manual check", Tone.NEUTRAL)
                    ok -> Badge("Allowed", Tone.SUCCESS, Icons.Filled.Check)
                    p.required -> Badge("Not enabled", Tone.WARNING)
                    else -> Badge("Optional", Tone.NEUTRAL)
                }
                Spacer(Modifier.weight(1f))
                if (!ok || (p == Perm.ACCESSIBILITY && stuck)) AppButton(if (p == Perm.ACCESSIBILITY && stuck) "Fix" else if (!Permissions.checkable(p)) "Check settings" else "Enable", {
                    if (p == Perm.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        runCatching { context.startActivity(Permissions.settingsIntent(context, p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    }
                }, fullWidth = false, kind = ButtonKind.SECONDARY)
            }
            if (p == Perm.ACCESSIBILITY && stuck) {
                Spacer(Modifier.height(12.dp))
                InlineBanner(
                    "Android stopped the blocker (this happens after an app update, or when the phone closes apps to save battery). " +
                        "Tap Fix → Habit Maker blocker → turn it OFF, then ON again." +
                        if (Permissions.isXiaomi) " On Xiaomi also allow Autostart and set battery to \"No restrictions\"." else "",
                    Tone.WARNING,
                )
            }
            if (p == Perm.AUTOSTART) {
                Spacer(Modifier.height(8.dp))
                Text("Tap Check settings and switch Habit Maker on. Also: Settings → Apps → Habit Maker → Battery saver → No restrictions.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            if (p == Perm.ACCESSIBILITY && !ok && !stuck && Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton({ showHelp = !showHelp }) {
                    Text(if (showHelp) "Hide setup help" else "Can't enable accessibility?", color = c.primary)
                }
                AnimatedVisibility(showHelp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("If Android shows a restricted setting, open App info, tap the ⋮ menu and choose Allow restricted settings. Then return here and tap Enable.",
                            style = MaterialTheme.typography.bodySmall, color = c.muted)
                        AppButton("Open app info", {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }, kind = ButtonKind.SECONDARY)
                    }
                }
            }
        }
    }
}
