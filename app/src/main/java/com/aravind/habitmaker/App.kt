package com.aravind.habitmaker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import com.aravind.habitmaker.core.prefs.TimeFormat
import com.aravind.habitmaker.core.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.aravind.habitmaker.core.auth.AuthRepository
import com.aravind.habitmaker.core.prefs.AppPrefs
import com.aravind.habitmaker.data.apps.InstalledApps
import com.aravind.habitmaker.data.db.AppDatabase
import com.aravind.habitmaker.data.usage.UsageRepository

/** App-wide singletons (simple manual DI). */
class App : Application() {
    val prefs by lazy { AppPrefs(this) }
    val auth by lazy { AuthRepository(this) }
    val db by lazy { AppDatabase.create(this) }
    val usage by lazy { UsageRepository(this) }
    val apps by lazy { InstalledApps(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannels()
        Clock.use24 = DateFormat.is24HourFormat(this)
        scope.launch {
            prefs.settings.collect { s ->
                Clock.use24 = when (s.timeFormat) {
                    TimeFormat.H12 -> false
                    TimeFormat.H24 -> true
                    TimeFormat.SYSTEM -> DateFormat.is24HourFormat(this@App)
                }
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Habit reminders", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_FOCUS_DONE, "Focus finished", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_ROUTINE, "Study routine", NotificationManager.IMPORTANCE_HIGH),
                // The alarm plays its own sound; the channel stays silent.
                NotificationChannel(CHANNEL_ALARM, "Wake-up alarm", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) },
            ),
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_FOCUS_DONE = "focus_done"
        const val CHANNEL_ROUTINE = "routine"
        const val CHANNEL_ALARM = "alarm"
    }
}

val Context.app: App get() = applicationContext as App
