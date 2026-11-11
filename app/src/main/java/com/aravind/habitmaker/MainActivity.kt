package com.aravind.habitmaker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.prefs.ThemeMode
import com.aravind.habitmaker.ui.RootScreen
import com.aravind.habitmaker.ui.theme.HabitMakerTheme

class MainActivity : ComponentActivity() {
    /** A tab to open, from a notification ("focus" …). */
    private val openRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openRoute.value = intent?.getStringExtra(EXTRA_OPEN)
        val prefs = app.prefs
        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(Settings())
            val mode = settings.theme
            val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val route by openRoute
            HabitMakerTheme(mode, settings.accent) { RootScreen(route) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { openRoute.value = it }
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
