package com.aravind.habitmaker.core.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.blocking.BlockerStatus
import com.aravind.habitmaker.service.BlockerService

enum class Perm(val title: String, val why: String, val required: Boolean) {
    USAGE("Usage access", "See screen time, unlocks and app limits.", true),
    ACCESSIBILITY("Accessibility service", "See the app on screen and show the block pop-up over blocked apps, Shorts and websites.", true),
    NOTIFICATIONS("Notifications", "Habit reminders and the focus timer.", false),
    EXACT_ALARM("Alarms & reminders", "Ring the wake-up alarm and send reminders at the exact time.", false),
    FULL_SCREEN("Full-screen alarm", "Show the wake-up alarm over the lock screen.", false),
    BATTERY("Unrestricted battery", "Keep blocking and timers working in the background.", false),
    AUTOSTART("Autostart (Xiaomi / Redmi / POCO)", "Let Habit Maker start again after the phone closes it, so the blocker keeps running.", false),
}

object Permissions {
    fun granted(context: Context, p: Perm): Boolean = when (p) {
        Perm.USAGE -> context.app.usage.hasAccess()
        // Switched on in Settings is not enough: the service must also be running.
        Perm.ACCESSIBILITY -> accessibilityOn(context) && BlockerStatus.state.value.running
        Perm.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        Perm.EXACT_ALARM -> Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Perm.BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        Perm.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Perm.AUTOSTART -> false // the phone doesn't tell apps; the user checks it
    }

    /** Can the app read this permission's state? (Xiaomi's Autostart can't be read.) */
    fun checkable(p: Perm) = p != Perm.AUTOSTART

    val isXiaomi: Boolean get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    /** The permissions that apply to this phone. */
    fun visible(): List<Perm> = Perm.entries.filter { it != Perm.AUTOSTART || isXiaomi }

    /** Switched on in Settings but Android stopped it (after an update, or the phone closed the app). */
    fun accessibilityStuck(context: Context) = accessibilityOn(context) && !BlockerStatus.state.value.running

    /** The settings page where the user turns [p] on. Notifications on Android 13+ use the runtime dialog instead. */
    fun settingsIntent(context: Context, p: Perm): Intent {
        val pkg = "package:${context.packageName}".toUri()
        return when (p) {
            Perm.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Perm.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Perm.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            Perm.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            Perm.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            Perm.AUTOSTART -> Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            Perm.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun accessibilityOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun requiredMissing(context: Context): Boolean = visible().any { it.required && !granted(context, it) }
}
