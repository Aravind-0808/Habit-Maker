package com.aravind.habitmaker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aravind.habitmaker.MainActivity
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatMinutes
import java.time.LocalDate

// Same zinc tokens as the app theme.
private val Bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF18181B))
private val Fg = ColorProvider(day = Color(0xFF09090B), night = Color(0xFFFAFAFA))
private val Muted = ColorProvider(day = Color(0xFF71717A), night = Color(0xFFA1A1AA))
private val PrimaryBg = ColorProvider(day = Color(0xFF18181B), night = Color(0xFFFAFAFA))
private val OnPrimary = ColorProvider(day = Color(0xFFFAFAFA), night = Color(0xFF18181B))

object Widgets {
    suspend fun refresh(context: Context) {
        runCatching {
            TodayWidget().updateAll(context)
            FocusWidget().updateAll(context)
            HabitsWidget().updateAll(context)
            CalendarWidget().updateAll(context)
        }
    }
}

private data class TodayData(val screen: String, val focus: String, val habits: String)

private suspend fun loadToday(context: Context): TodayData {
    val app = context.app
    val usage = app.usage.today()
    val focusMin = app.db.focus().since(Dates.startOfToday()).sumOf { it.actualMin }
    val today = LocalDate.now()
    val due = app.db.habits().all().filter { !it.archived && it.days and Dates.bit(today.dayOfWeek) != 0 }
    val logs = app.db.habitLogs().forDay(today.toEpochDay()).associateBy { it.habitId }
    val done = due.count { (logs[it.id]?.count ?: 0) >= it.target }
    return TodayData(
        screen = if (app.usage.hasAccess()) formatDuration(usage.totalMs) else "—",
        focus = formatMinutes(focusMin),
        habits = "$done/${due.size}",
    )
}

/** Today: screen time, focus minutes and habits done. Tapping opens the app. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadToday(context)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text("Today", style = TextStyle(color = Muted, fontSize = 12.sp))
                Text(d.screen, style = TextStyle(color = Fg, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Text("screen time", style = TextStyle(color = Muted, fontSize = 11.sp))
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("Focus", d.focus)
                    Spacer(GlanceModifier.width(16.dp))
                    Stat("Habits", d.habits)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = TextStyle(color = Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = Muted, fontSize = 11.sp))
    }
}

/** One tap starts a focus session of the default length. */
class FocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val minutes = context.app.prefs.current().defaultFocusMin
        val running = FocusTimer.running
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(PrimaryBg).cornerRadius(20.dp).padding(14.dp)
                    .clickable(if (running) actionStartActivity<MainActivity>() else actionRunCallback<StartFocusAction>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (running) "Focusing…" else "Start focus", style = TextStyle(color = OnPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(if (running) "Tap to open" else "$minutes min", style = TextStyle(color = OnPrimary, fontSize = 12.sp))
            }
        }
    }
}

class StartFocusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val s = context.app.prefs.current()
        FocusTimer.start(context, s.defaultFocusMin, "Focus")
        FocusWidget().updateAll(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}
