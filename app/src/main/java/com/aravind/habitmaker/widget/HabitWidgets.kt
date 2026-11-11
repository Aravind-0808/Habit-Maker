package com.aravind.habitmaker.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aravind.habitmaker.MainActivity
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.feature.habits.HabitActions
import java.time.LocalDate

private val Bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF18181B))
private val Fg = ColorProvider(day = Color(0xFF09090B), night = Color(0xFFFAFAFA))
private val Muted = ColorProvider(day = Color(0xFF71717A), night = Color(0xFFA1A1AA))
private val Empty = ColorProvider(day = Color(0xFFF4F4F5), night = Color(0xFF27272A))

private data class WidgetHabit(val id: Long, val name: String, val color: Long, val count: Int, val target: Int) {
    val done get() = count >= target
}

/** Today's habits; tap one to tick it off without opening the app. */
class HabitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val today = LocalDate.now()
        val logs = app.db.habitLogs().forDay(today.toEpochDay()).associateBy { it.habitId }
        val habits = app.db.habits().all()
            .filter { !it.archived && it.days and Dates.bit(today.dayOfWeek) != 0 }
            .map { WidgetHabit(it.id, it.name, it.color, logs[it.id]?.count ?: 0, it.target) }
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(14.dp)) {
                Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's habits", style = TextStyle(color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                    Text("${habits.count { it.done }}/${habits.size}", style = TextStyle(color = Muted, fontSize = 12.sp))
                }
                Spacer(GlanceModifier.height(8.dp))
                if (habits.isEmpty()) {
                    Text("Nothing due today", style = TextStyle(color = Muted, fontSize = 12.sp))
                }
                habits.take(6).forEach { h ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = ColorProvider(day = Color(h.color), night = Color(h.color))
                        Box(
                            GlanceModifier.size(20.dp).cornerRadius(10.dp).background(if (h.done) tint else Empty),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (h.done) Text("✓", style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Text(h.name, style = TextStyle(color = if (h.done) Muted else Fg, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        if (h.target > 1) Text("${h.count}/${h.target}", style = TextStyle(color = Muted, fontSize = 11.sp))
                    }
                }
                if (habits.size > 6) Text("+${habits.size - 6} more", style = TextStyle(color = Muted, fontSize = 11.sp))
            }
        }
    }
}

val HabitIdKey = ActionParameters.Key<Long>("habitId")

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        val habit = context.app.db.habits().get(id) ?: return
        HabitActions.tap(context, habit) // also refreshes every widget
    }
}

/** Last 4 weeks: each day coloured by how many of its habits were done. */
class CalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val today = LocalDate.now()
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val start = thisMonday.minusWeeks(3)
        val habits = app.db.habits().all().filter { !it.archived }
        val logs = app.db.habitLogs().all().filter { it.day >= start.toEpochDay() }
        // -1 = future / nothing due, else 0..1 share done
        val share = (0 until 28).map { i ->
            val d = start.plusDays(i.toLong())
            val due = habits.filter { it.days and Dates.bit(d.dayOfWeek) != 0 && Dates.dayOf(it.createdAt) <= d }
            when {
                d.isAfter(today) || due.isEmpty() -> -1f
                else -> due.count { h -> logs.any { it.habitId == h.id && it.day == d.toEpochDay() && it.count >= h.target } }.toFloat() / due.size
            }
        }
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text("Habit calendar", style = TextStyle(color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Dates.SHORT_DAYS.forEach { l ->
                        Text(l, style = TextStyle(color = Muted, fontSize = 10.sp), modifier = GlanceModifier.width(26.dp))
                    }
                }
                (0 until 4).forEach { w ->
                    Spacer(GlanceModifier.height(4.dp))
                    Row {
                        (0 until 7).forEach { dow ->
                            val v = share[w * 7 + dow]
                            Box(GlanceModifier.size(22.dp).cornerRadius(6.dp).background(level(v))) {}
                            Spacer(GlanceModifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }

    private fun level(v: Float): GlanceColor = when {
        v < 0f -> ColorProvider(day = Color(0x00000000), night = Color(0x00000000))
        v == 0f -> Empty
        v < 0.5f -> ColorProvider(day = Color(0x6610B981), night = Color(0x6610B981))
        v < 1f -> ColorProvider(day = Color(0xAA10B981), night = Color(0xAA10B981))
        else -> ColorProvider(day = Color(0xFF10B981), night = Color(0xFF10B981))
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}
