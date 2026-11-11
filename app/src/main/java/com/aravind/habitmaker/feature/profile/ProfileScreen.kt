package com.aravind.habitmaker.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.aravind.habitmaker.ui.theme.Accent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.auth.AuthState
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.prefs.ThemeMode
import com.aravind.habitmaker.core.prefs.TimeFormat
import com.aravind.habitmaker.core.reminders.HabitReminders
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.backup.Backup
import com.aravind.habitmaker.feature.blocking.NavRow
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.Avatar
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.ConfirmDialog
import com.aravind.habitmaker.ui.components.Divider
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.MenuRow
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Segmented
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface Edit {
    data class Number(val title: String, val value: Int, val range: IntRange, val step: Int, val save: (Settings, Int) -> Settings) : Edit
    data object Message : Edit
    data object Name : Edit
}

/** Account, preferences, data and app info. */
@Composable
fun ProfileScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val user by app.auth.state.collectAsStateWithLifecycle(AuthState())
    val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
    var edit by remember { mutableStateOf<Edit?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { Backup.export(context, uri) }.fold({ "Backup saved." to Tone.SUCCESS }, { "Couldn't save: ${it.message}" to Tone.DANGER })
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingImport = uri; confirm = "import" }
    }

    fun set(change: (Settings) -> Settings) = scope.launch { app.prefs.update(change) }

    when (val e = edit) {
        is Edit.Number -> NumberDialog(e.title, e.value, e.range, e.step, { edit = null }) { v -> set { e.save(it, v) } }
        Edit.Message -> TextDialog("Block screen message", s.overlayMessage, { edit = null }) { t -> set { it.copy(overlayMessage = t) } }
        Edit.Name -> TextDialog("Your name", user.name, { edit = null }) { t -> scope.launch { app.auth.updateName(t) } }
        null -> Unit
    }
    when (confirm) {
        "signout" -> ConfirmDialog("Sign out?", "Your data stays on this phone.", "Sign out", { scope.launch { app.auth.signOut() } }, { confirm = null }, danger = false)
        "delete" -> ConfirmDialog(
            "Delete all data?", "Habits, focus history, block rules and your account on this phone will be erased. This can't be undone.", "Delete everything",
            {
                scope.launch {
                    Backup.wipe(context)
                    app.auth.resetAccount()
                    app.prefs.update { Settings(theme = it.theme) }
                }
            }, { confirm = null },
        )
        "import" -> ConfirmDialog(
            "Restore backup?", "This replaces all habits, history and block rules on this phone with the backup's.", "Restore",
            {
                val uri = pendingImport ?: return@ConfirmDialog
                scope.launch {
                    message = runCatching { Backup.import(context, uri) }.fold({ "Backup restored." to Tone.SUCCESS }, { "Couldn't restore: ${it.message}" to Tone.DANGER })
                }
            }, { confirm = null }, danger = false,
        )
    }

    ScrollScreen("Profile", onBack = onBack) {
        message?.let { (text, tone) -> InlineBanner(text, tone) }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(user.initials, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (user.signedIn) user.name else "Guest", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    Text(if (user.signedIn) user.email else "Not signed in", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                if (user.signedIn) IconButton({ edit = Edit.Name }) { Icon(Icons.Filled.Edit, "Edit name", tint = c.muted) }
            }
            if (!user.signedIn) {
                Spacer(Modifier.height(12.dp))
                AppButton("Sign in or create account", { scope.launch { app.auth.signOut() } }, kind = ButtonKind.SECONDARY)
            }
        }

        Column {
            SectionLabel("Appearance")
            Segmented(listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), s.theme, { m -> set { it.copy(theme = m) } })
            Spacer(Modifier.height(12.dp))
            AccentPicker(s.accent) { a -> set { it.copy(accent = a) } }
            Spacer(Modifier.height(16.dp))
            Text("Time format", style = MaterialTheme.typography.labelMedium, color = c.muted)
            Spacer(Modifier.height(6.dp))
            Segmented(TimeFormat.entries.map { it to it.label }, s.timeFormat, { f -> set { it.copy(timeFormat = f) } })
        }

        Column {
            SectionLabel("Goals & focus")
            AppCard(padding = 0.dp) {
                MenuRow(Icons.Filled.Flag, "Daily screen-time target", { edit = Edit.Number("Daily screen-time target (min)", s.dailyTargetMin, 30..480, 30) { st, v -> st.copy(dailyTargetMin = v) } }, value = formatMinutes(s.dailyTargetMin))
                Divider()
                MenuRow(Icons.Filled.Timer, "Default focus length", { edit = Edit.Number("Default focus length (min)", s.defaultFocusMin, 5..120, 5) { st, v -> st.copy(defaultFocusMin = v) } }, value = formatMinutes(s.defaultFocusMin))
                Divider()
                MenuRow(Icons.Filled.Coffee, "Break length", { edit = Edit.Number("Break length (min)", s.breakMin, 1..30, 1) { st, v -> st.copy(breakMin = v) } }, value = formatMinutes(s.breakMin))
            }
        }

        Column {
            SectionLabel("Notifications & blocking")
            AppCard(padding = 0.dp) {
                MenuRow(Icons.Filled.Notifications, "Habit reminders", null, trailing = {
                    Switch(s.remindersEnabled, { on ->
                        scope.launch { app.prefs.update { it.copy(remindersEnabled = on) }; HabitReminders.rescheduleAll(context) }
                    }, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
                })
                Divider()
                MenuRow(Icons.Filled.ChatBubbleOutline, "Block screen message", { edit = Edit.Message })
                Divider()
                NavRow(Icons.Filled.AdminPanelSettings, "Permissions", "") { go(Routes.PERMISSIONS) }
            }
        }

        Column {
            SectionLabel("Your data")
            AppCard(padding = 0.dp) {
                MenuRow(Icons.Filled.Download, "Export backup", { exporter.launch("habitmaker-backup-${LocalDate.now()}.json") })
                Divider()
                MenuRow(Icons.Filled.Upload, "Restore from backup", { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
        }

        Column {
            SectionLabel("More")
            AppCard(padding = 0.dp) {
                NavRow(Icons.Filled.Star, "All features", "Free") { go(Routes.PREMIUM) }
                Divider()
                NavRow(Icons.AutoMirrored.Filled.HelpOutline, "Help", "") { go(Routes.info("help")) }
                Divider()
                NavRow(Icons.Filled.Shield, "Privacy policy", "") { go(Routes.info("privacy")) }
                Divider()
                NavRow(Icons.Filled.Info, "About", "") { go(Routes.info("about")) }
            }
        }

        AppCard(padding = 0.dp) {
            if (user.signedIn) {
                MenuRow(Icons.AutoMirrored.Filled.Logout, "Sign out", { confirm = "signout" })
                Divider()
            }
            MenuRow(Icons.Filled.DeleteForever, "Delete all data", { confirm = "delete" }, tint = c.danger)
        }
    }
}

@Composable
private fun NumberDialog(title: String, value: Int, range: IntRange, step: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val c = MaterialTheme.colors
    var v by remember { mutableIntStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(formatMinutes(v), style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                Slider(v.toFloat(), { v = ((it / step).toInt() * step).coerceIn(range) }, valueRange = range.first.toFloat()..range.last.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken))
            }
        },
        confirmButton = { TextButton({ onSave(v); onDismiss() }) { Text("Save", color = c.foreground, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

@Composable
private fun TextDialog(title: String, value: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = MaterialTheme.colors
    var t by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { AppTextField(t, { t = it }, title, singleLine = false) },
        confirmButton = { TextButton({ if (t.isNotBlank()) { onSave(t.trim()); onDismiss() } }) { Text("Save", color = c.foreground, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: Accent, onPick: (Accent) -> Unit) {
    val c = MaterialTheme.colors
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Accent.entries.forEach { a ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(a) }) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(if (c.isDark) a.dark else a.light)
                        .border(3.dp, if (a == selected) c.foreground else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (a == selected) Icon(Icons.Filled.Check, null, tint = if (c.isDark) Color(0xFF09090B) else Color.White, modifier = Modifier.size(18.dp)) }
                Text(a.label, style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
        }
    }
}
