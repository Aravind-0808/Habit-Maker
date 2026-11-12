package com.aravind.habitmaker.feature.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.ui.components.SkeletonScreen
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.db.AppRule
import com.aravind.habitmaker.data.usage.DayUsage
import com.aravind.habitmaker.feature.blocking.LimitDialog
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppIcon
import com.aravind.habitmaker.ui.components.BarChart
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.LoadingState
import com.aravind.habitmaker.ui.components.Metric
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.Segmented
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

/** One app: 7-day chart, its limit and whether it's distracting. */
@Composable
fun AppUsageScreen(pkg: String, onBack: () -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val label = remember(pkg) { app.apps.label(pkg) }
    val month by produceState<List<DayUsage>?>(null, pkg) { value = app.usage.lastDays(30) }
    var span by remember { mutableIntStateOf(7) }
    val loadedRules by remember { app.db.appRules().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val rules = loadedRules ?: run { SkeletonScreen(label, onBack); return }
    val rule = rules.firstOrNull { it.packageName == pkg } ?: AppRule(pkg)
    var editLimit by remember { mutableStateOf(false) }

    if (editLimit) LimitDialog(pkg, label, rule.dailyLimitMin) { editLimit = false }

    ScrollScreen(label, onBack = onBack) {
        val all = month ?: run { LoadingState(); return@ScrollScreen }
        val list = all.takeLast(span)
        val per = list.map { d -> d.byApp.firstOrNull { it.first == pkg }?.second ?: 0L }
        val labels = list.mapIndexed { i, d ->
            if (span == 30) (if (i % 5 == 4 || i == list.lastIndex) "${d.day.dayOfMonth}" else "") else Dates.SHORT_DAYS[d.day.dayOfWeek.value - 1]
        }

        Segmented(listOf(7 to "7 days", 30 to "30 days"), span, { span = it })

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 48.dp)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, color = c.foreground)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Metric(formatDuration(per.last()), "Today", Modifier.weight(1f))
                Metric(formatDuration(per.sum() / span), "Daily average", Modifier.weight(1f))
                Metric(formatDuration(per.sum()), if (span == 7) "This week" else "30 days", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            BarChart(per.map { it / 60_000f }, labels)
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.HourglassBottom, c.info)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily limit", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text(if (rule.dailyLimitMin > 0) formatMinutes(rule.dailyLimitMin) else "No limit", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                AppButton(if (rule.dailyLimitMin > 0) "Change" else "Set", { editLimit = true }, kind = ButtonKind.SECONDARY, fullWidth = false)
            }
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Block, c.danger)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Distracting app", style = MaterialTheme.typography.titleSmall, color = c.foreground)
                    Text("Blocked during focus, \"Block now\" and the schedule", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(rule.distracting, { on -> scope.launch { app.db.appRules().upsert(rule.copy(distracting = on)) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
            }
        }
    }
}
