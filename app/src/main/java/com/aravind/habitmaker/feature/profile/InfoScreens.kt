package com.aravind.habitmaker.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.Amber
import com.aravind.habitmaker.ui.theme.colors

/** Help, privacy policy and about, as plain text sections. */
@Composable
fun InfoScreen(kind: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val (title, sections) = when (kind) {
        "help" -> "Help" to HELP
        "privacy" -> "Privacy policy" to PRIVACY
        else -> "About" to listOf(
            "Habit Maker" to "Version $version\nBuild good habits, focus deeply and use your phone with intention.",
            "Made by" to "Redmind Technologies",
        )
    }
    val c = MaterialTheme.colors
    ScrollScreen(title, onBack = onBack) {
        sections.forEach { (h, body) ->
            AppCard {
                Text(h, style = MaterialTheme.typography.titleSmall, color = c.foreground)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        }
    }
}

private val HELP = listOf(
    "Blocking doesn't work" to "Open Profile → Permissions and turn on the Accessibility service and Usage access. Some phones (Xiaomi, Oppo, Vivo, Samsung) also stop background apps: allow \"Unrestricted battery\" for Habit Maker.",
    "Screen time shows —" to "Usage access is off. Turn it on in Profile → Permissions.",
    "Reminders don't arrive" to "Allow notifications and \"Alarms & reminders\" in Profile → Permissions, and check the reminder switch in Profile.",
    "Shorts / Reels blocking" to "Works in the YouTube and Instagram apps. Apps change their screens often, so if it stops working, update Habit Maker.",
    "Website blocking" to "Works in the address bar of Chrome, Samsung Internet, Firefox, Edge, Brave, Opera and DuckDuckGo. Adding a site also blocks its subdomains.",
    "Strict mode" to "A strict focus session or \"Block now\" can't be stopped early, and Habit Maker's settings and uninstall page are blocked until it ends. Use it when you really mean it.",
    "Schedules" to "Block → Schedules: add as many as you like (study, work, sleep). Distracting apps are blocked during every schedule that is on.",
    "Backups" to "Profile → Export backup saves a file with all your habits, history and rules. Restore it on a new phone from the same menu.",
)

private val PRIVACY = listOf(
    "Summary" to "Habit Maker works on your phone. Your habits, focus sessions, screen time and block rules are stored only on this device. Nothing is sent to a server.",
    "Accessibility service" to "Used only to see which app is in front, and for a few apps the page shown (YouTube Shorts, Instagram Reels, a browser's address bar), so your blocks can work. Screen content is not stored or shared.",
    "Usage access" to "Used to show your screen time and unlocks, and to enforce app limits. The data stays on this phone.",
    "Account" to "Your name, email and a salted hash of your password are kept on this phone only.",
    "Backups" to "A backup file is created only when you choose Export. You decide where it is saved.",
    "Contact" to "Redmind Technologies.",
)

private val ALL_FEATURES = listOf(
    Triple(Icons.Filled.CheckCircle, "Unlimited habits", "Streaks, reminders, calendar and history."),
    Triple(Icons.Filled.Timer, "Focus timer", "Breaks, strict mode and focus history."),
    Triple(Icons.Filled.Block, "Advanced blocking", "Distracting apps, daily limits, several schedules, websites, Shorts & Reels."),
    Triple(Icons.Filled.Lock, "Uninstall protection", "During strict mode, Habit Maker can't be switched off or uninstalled."),
    Triple(Icons.Filled.BarChart, "Detailed reports", "Today, 7-day and 30-day trends, plus history for every app."),
    Triple(Icons.Filled.Widgets, "4 widgets", "Today, Focus, Habit checklist and Habit calendar."),
    Triple(Icons.Filled.Palette, "Colour themes", "Six accent colours, light and dark."),
    Triple(Icons.Filled.Download, "Backup & restore", "Save everything to a file and restore it on any phone."),
)

/** Everything is included for free — no subscription. */
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val c = MaterialTheme.colors
    ScrollScreen("All features", onBack = onBack) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconTile(Icons.Filled.Star, Amber, 64.dp)
            Spacer(Modifier.height(12.dp))
            Text("Everything is free", style = MaterialTheme.typography.headlineSmall, color = c.foreground)
            Text("No ads, no subscription. Every feature is unlocked.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        AppCard {
            ALL_FEATURES.forEachIndexed { i, (icon, t, d) ->
                if (i > 0) Spacer(Modifier.height(14.dp))
                Feature(icon, t, d)
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, desc: String) {
    val c = MaterialTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.foreground, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Icon(Icons.Filled.Check, "Included", tint = c.success)
    }
}
