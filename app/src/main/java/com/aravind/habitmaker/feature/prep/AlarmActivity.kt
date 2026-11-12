package com.aravind.habitmaker.feature.prep

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.data.db.ExamPlan
import com.aravind.habitmaker.core.util.formatTime
import com.aravind.habitmaker.service.AlarmService
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.theme.HabitMakerTheme
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Full-screen alarm, shown over the lock screen. Closes itself when the alarm stops. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // Back does nothing: choose Stop or Snooze.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })

        setContent {
            val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
            val ring by AlarmService.ringing.collectAsStateWithLifecycle()
            val plan by produceState<ExamPlan?>(null) { value = app.db.routine().plan() }
            val time by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(1000) } }
            // Give the service a moment to start, then close once it's not ringing.
            LaunchedEffect(ring) { if (ring == null) { delay(1500); if (AlarmService.ringing.value == null) finish() } }

            HabitMakerTheme(s.theme, s.accent, s.secondaryAccent, s.primaryColor, s.secondaryColor) {
                val c = MaterialTheme.colors
                Column(
                    Modifier.fillMaxSize().background(c.background).systemBarsPadding().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconTile(Icons.Filled.Alarm, c.warning, 72.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(formatTime(time), style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (com.aravind.habitmaker.core.util.Clock.use24) 64.sp else 48.sp), color = c.foreground)
                    Spacer(Modifier.height(8.dp))
                    Text(ring?.title ?: "Wake up", style = MaterialTheme.typography.headlineSmall, color = c.foreground, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    val p = plan
                    val days = p?.examDay?.let { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(it)) }
                    Text(
                        when {
                            p == null -> "Good morning! Let's make today count."
                            days != null && days > 0 -> "Good morning! ${p.exam} is in $days days."
                            days == 0L -> "Today is your ${p.exam} exam. You've got this!"
                            else -> "Good morning! Time to prepare for ${p.exam}."
                        },
                        style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(48.dp))
                    AppButton("Stop — I'm up", { AlarmService.stop(this@AlarmActivity); finish() }, modifier = Modifier.widthIn(max = 320.dp))
                    Spacer(Modifier.height(12.dp))
                    AppButton("Snooze ${s.alarmSnoozeMin} min", { AlarmService.snooze(this@AlarmActivity); finish() },
                        modifier = Modifier.widthIn(max = 320.dp), kind = ButtonKind.SECONDARY, icon = Icons.Filled.Snooze)
                }
            }
        }
    }
}
