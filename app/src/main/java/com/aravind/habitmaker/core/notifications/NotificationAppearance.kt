package com.aravind.habitmaker.core.notifications

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.prefs.ThemeMode
import com.aravind.habitmaker.ui.theme.resolveAppColors

internal fun notificationAccent(context: Context, settings: Settings): Int {
    val dark = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    return resolveAppColors(dark, settings.accent, settings.secondaryAccent, settings.primaryColor, settings.secondaryColor).primary.toArgb()
}

internal suspend fun notificationAccent(context: Context): Int = notificationAccent(context, context.app.prefs.current())
