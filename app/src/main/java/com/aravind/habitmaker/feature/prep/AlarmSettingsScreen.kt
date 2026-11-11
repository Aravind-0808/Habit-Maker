package com.aravind.habitmaker.feature.prep

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.permissions.Perm
import com.aravind.habitmaker.core.permissions.Permissions
import com.aravind.habitmaker.core.prefs.FlashMode
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.service.AlarmService
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.Chip
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Segmented
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

/** Wake-up alarm: sound (phone tones or any song), vibration, flashlight blink, snooze, test. */
@Composable
fun AlarmSettingsScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
    val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose {} }
    val alarmsOk = remember(resumes) { Permissions.granted(context, Perm.EXACT_ALARM) && Permissions.granted(context, Perm.FULL_SCREEN) }
    val hasFlash = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }

    fun set(change: (Settings) -> Settings) = scope.launch { app.prefs.update(change) }

    // Phone's alarm tones
    val tonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        if (uri == null) set { it.copy(alarmSound = "", alarmSoundName = "Default alarm") }
        else {
            val name = runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }.getOrNull() ?: "Alarm tone"
            set { it.copy(alarmSound = uri.toString(), alarmSoundName = name) }
        }
    }
    // Any song / audio file on the phone
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        }.getOrNull() ?: "My song"
        set { it.copy(alarmSound = uri.toString(), alarmSoundName = name.substringBeforeLast('.')) }
    }

    ScrollScreen("Alarm", onBack = onBack) {
        if (!alarmsOk) {
            InlineBanner("To ring on time and over the lock screen, allow \"Alarms & reminders\" and \"Full-screen alarm\".", Tone.WARNING)
            AppButton("Open permissions", { go(Routes.PERMISSIONS) }, kind = ButtonKind.SECONDARY)
        }

        Column {
            SectionLabel("Sound")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.MusicNote, c.foreground)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.alarmSoundName, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                        Text(if (s.alarmSound.isEmpty()) "The phone's alarm sound" else "Your choice", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("Alarm tones", {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.alarmSound.takeIf { it.isNotEmpty() }?.let(Uri::parse))
                        runCatching { tonePicker.launch(i) }
                    }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Filled.LibraryMusic)
                    AppButton("My songs", { filePicker.launch(arrayOf("audio/*")) }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Filled.AudioFile)
                }
                if (s.alarmSound.isNotEmpty()) {
                    AppButton("Use default sound", { set { it.copy(alarmSound = "", alarmSoundName = "Default alarm") } }, kind = ButtonKind.GHOST)
                }
            }
        }

        Column {
            SectionLabel("While ringing")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.VolumeUp, c.info, 32.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { ToggleRow("Gentle start", "Volume starts low and rises.", s.alarmRamp) { on -> set { it.copy(alarmRamp = on) } } }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Vibration, c.info, 32.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { ToggleRow("Vibrate", "Buzz along with the sound.", s.alarmVibrate) { on -> set { it.copy(alarmVibrate = on) } } }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.FlashlightOn, c.warning, 32.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        ToggleRow("Flash blink", if (hasFlash) "The flashlight blinks until you stop the alarm." else "This phone has no flashlight.", s.alarmFlash && hasFlash) { on ->
                            if (hasFlash) set { it.copy(alarmFlash = on) }
                        }
                    }
                }
                if (s.alarmFlash && hasFlash) {
                    Spacer(Modifier.height(10.dp))
                    Segmented(FlashMode.entries.map { it to it.label }, s.alarmFlashMode, { m -> set { it.copy(alarmFlashMode = m) } })
                }
            }
        }

        Column {
            SectionLabel("Snooze")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10, 15).forEach { m -> Chip("$m min", s.alarmSnoozeMin == m, { set { it.copy(alarmSnoozeMin = m) } }) }
            }
        }

        if (ringing != null) {
            AppButton("Stop test", { AlarmService.stop(context) }, kind = ButtonKind.DANGER, icon = Icons.Filled.Stop)
        } else {
            AppButton("Test alarm", { AlarmService.start(context, -1, "Test alarm") }, icon = Icons.Filled.Alarm)
        }
        Text("The wake-up time is the \"Wake up\" block in your routine (Exam Prep → Edit).", style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (Build.VERSION.SDK_INT >= 33) Text("Tip: keep notifications allowed so the alarm can show over the lock screen.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}
