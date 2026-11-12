package com.aravind.habitmaker.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.core.prefs.Goal
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppLogo
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.theme.Blue
import com.aravind.habitmaker.ui.theme.Emerald
import com.aravind.habitmaker.ui.theme.Rose
import com.aravind.habitmaker.ui.theme.Violet
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

private const val PAGES = 4

/** Welcome → goal → daily target → what the app does. */
@Composable
fun OnboardingScreen(onDone: (Goal, Int) -> Unit) {
    val c = MaterialTheme.colors
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf(Goal.LESS_PHONE) }
    var target by remember { mutableIntStateOf(180) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < PAGES - 1) {
                AppButton("Skip", { onDone(goal, target) }, kind = ButtonKind.GHOST, fullWidth = false)
            } else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> Intro(
                        null, c.primary, "Welcome to Habit Maker",
                        "Build good habits, focus deeply and use your phone with intention.",
                    )
                    1 -> {
                        Intro(Icons.Filled.Flag, c.primary, "What's your main goal?", "We'll shape the app around it.")
                        Spacer(Modifier.height(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Goal.entries.forEach { g ->
                                AppCard(onClick = { goal = g }, borderColor = if (goal == g) c.foreground else null) {
                                    Text(g.label, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                                }
                            }
                        }
                    }
                    2 -> {
                        Intro(Icons.Filled.Timer, c.primary, "Daily screen-time target", "How much phone time a day feels right to you?")
                        Spacer(Modifier.height(32.dp))
                        Text(formatMinutes(target), style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = target.toFloat(), onValueChange = { target = (it / 30).toInt() * 30 },
                            valueRange = 30f..480f, steps = 14,
                            colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken),
                        )
                    }
                    else -> {
                        Intro(Icons.Filled.CheckCircle, c.primary, "You're all set", "Here's what Habit Maker does for you:")
                        Spacer(Modifier.height(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Feature(Icons.Filled.CheckCircle, "Track daily habits and streaks")
                            Feature(Icons.Filled.Timer, "Focus timer with breaks")
                            Feature(Icons.Filled.Block, "Block distracting apps, Shorts and websites")
                            Feature(Icons.Filled.BarChart, "See your screen time and unlocks")
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 20.dp else 8.dp, 8.dp)
                        .clip(CircleShape).background(if (i == pager.currentPage) c.foreground else c.borderStrong),
                )
            }
        }
        AppButton(
            if (pager.currentPage == PAGES - 1) "Get started" else "Next",
            {
                if (pager.currentPage == PAGES - 1) onDone(goal, target)
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun Intro(icon: ImageVector?, tint: androidx.compose.ui.graphics.Color, title: String, text: String) {
    val c = MaterialTheme.colors
    if (icon == null) AppLogo(96.dp) else IconTile(icon, tint, 72.dp)
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = c.foreground, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center)
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    val c = MaterialTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.foreground, 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.foreground)
    }
}
