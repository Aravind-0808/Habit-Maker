package com.aravind.habitmaker.feature.permissions

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
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
import com.aravind.habitmaker.ui.components.ScrollScreen
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
    val status = remember(tick) { Permissions.visible().associateWith { Permissions.granted(context, it) } }

    ScrollScreen("Permissions", onBack = onBack) {
        Text(
            "Habit Maker needs a few special permissions to block apps and read screen time. Everything stays on your phone.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted,
        )
        PermissionCards(Permissions.visible())
        if (onDone != null) {
            val allRequired = Permissions.visible().filter { it.required }.all { status[it] == true }
            AppButton(if (allRequired) "Continue" else "Continue for now", onDone, kind = if (allRequired) ButtonKind.PRIMARY else ButtonKind.SECONDARY)
            if (!allRequired) Text(
                "You can allow these later from Profile → Permissions. Blocking and stats won't work until then.",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
        }
    }
}

/** One card per permission with its status and an Allow button. Used by the Permissions page and Exam Prep setup. */
@Composable
fun PermissionCards(perms: List<Perm>) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose {} }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val blocker by BlockerStatus.state.collectAsStateWithLifecycle()
    val status = remember(tick, blocker.running) { perms.associateWith { Permissions.granted(context, it) } }
    val stuck = remember(tick, blocker.running) { Permissions.accessibilityStuck(context) }

    perms.forEach { p ->
        val ok = status[p] == true
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(p.icon(), if (ok) c.success else c.foreground)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text(p.why, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    p == Perm.ACCESSIBILITY && stuck -> Badge("On, but stopped", Tone.DANGER)
                    !Permissions.checkable(p) -> Badge("Please check", Tone.NEUTRAL)
                    ok -> Badge("Allowed", Tone.SUCCESS, Icons.Filled.Check)
                    p.required -> Badge("Required", Tone.WARNING)
                    else -> Badge("Recommended", Tone.NEUTRAL)
                }
                Spacer(Modifier.weight(1f))
                if (!ok || (p == Perm.ACCESSIBILITY && stuck)) AppButton(if (p == Perm.ACCESSIBILITY && stuck) "Fix" else if (!Permissions.checkable(p)) "Open" else "Allow", {
                    if (p == Perm.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        runCatching { context.startActivity(Permissions.settingsIntent(context, p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    }
                }, fullWidth = false)
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
                Text("Tap Open and switch Habit Maker on. Also: Settings → Apps → Habit Maker → Battery saver → No restrictions.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            if (p == Perm.ACCESSIBILITY && !ok && !stuck && Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(12.dp))
                InlineBanner(
                    "Switch greyed out (\"Restricted setting\")? Android blocks it for apps installed from an APK file. " +
                        "Tap \"App info\" → ⋮ menu (top right) → \"Allow restricted settings\", then come back and tap Allow again.",
                    Tone.INFO,
                )
                Spacer(Modifier.height(8.dp))
                AppButton("App info", {
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
