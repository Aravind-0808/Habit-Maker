package com.aravind.habitmaker.feature.blocking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.data.apps.InstalledApp
import com.aravind.habitmaker.data.db.AppRule
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppIcon
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.Divider
import com.aravind.habitmaker.ui.components.LoadingState
import com.aravind.habitmaker.ui.components.Segmented
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

/** Apps with a short-video section that can be blocked on its own (package → its name). */
val SHORT_VIDEO_APPS = linkedMapOf(
    "com.google.android.youtube" to "Shorts",
    "com.instagram.android" to "Reels",
)

enum class AppBlockMode { ALLOW, SHORTS, ALL }

fun AppRule?.mode(): AppBlockMode = when {
    this == null -> AppBlockMode.ALLOW
    distracting -> AppBlockMode.ALL
    shortsOnly -> AppBlockMode.SHORTS
    else -> AppBlockMode.ALLOW
}

/**
 * YouTube / Instagram: Allow · Shorts (Reels) only · Whole app, while focus, "block now" or a
 * schedule is on. The same rules are used everywhere (Block tab, Exam Prep, focus).
 */
@Composable
fun ShortVideoAppsCard() {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val installed by produceState<Set<String>?>(null) { value = app.apps.list().map { it.packageName }.toSet() }
    val loadedRules by remember { app.db.appRules().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val rules = loadedRules ?: run { LoadingState(); return }
    val byPkg = rules.associateBy { it.packageName }
    if (installed == null) { LoadingState(); return }
    val apps = SHORT_VIDEO_APPS.filterKeys { installed?.contains(it) == true }
    if (apps.isEmpty()) return

    AppCard {
        apps.entries.forEachIndexed { i, (pkg, shorts) ->
            if (i > 0) { Spacer(Modifier.height(12.dp)); Divider(); Spacer(Modifier.height(12.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg)
                Spacer(Modifier.width(12.dp))
                Text(app.apps.label(pkg), style = MaterialTheme.typography.titleSmall, color = c.foreground)
            }
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf(AppBlockMode.ALLOW to "Allow", AppBlockMode.SHORTS to "$shorts only", AppBlockMode.ALL to "Whole app"),
                byPkg[pkg].mode(),
                { m ->
                    scope.launch {
                        val r = byPkg[pkg] ?: AppRule(pkg)
                        app.db.appRules().upsert(r.copy(distracting = m == AppBlockMode.ALL, shortsOnly = m == AppBlockMode.SHORTS))
                    }
                },
            )
        }
    }
}

/** All other launcher apps with a tick box (not lazy, so it fits inside a scrolling page). */
@Composable
fun AppChecklist() {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val all by produceState<List<InstalledApp>?>(null) { value = app.apps.list() }
    val loadedRules by remember { app.db.appRules().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val rules = loadedRules ?: run { LoadingState(); return }
    val byPkg = rules.associateBy { it.packageName }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }

    val list = all ?: run { LoadingState(); return }
    AppTextField(query, { query = it }, "Search apps", leading = Icons.Filled.Search, imeAction = ImeAction.Search)
    val shown = list.filter { it.packageName !in SHORT_VIDEO_APPS && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
        .sortedByDescending { byPkg[it.packageName]?.distracting == true }
    val limit = if (showAll || query.isNotBlank()) shown.size else 12
    AppCard(padding = 0.dp) {
        shown.take(limit).forEachIndexed { i, a ->
            if (i > 0) Divider()
            val on = byPkg[a.packageName]?.distracting == true
            fun toggle() = scope.launch {
                val r = byPkg[a.packageName] ?: AppRule(a.packageName)
                app.db.appRules().upsert(r.copy(distracting = !on))
            }
            Row(
                Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(a.packageName, 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(a.label, style = MaterialTheme.typography.bodyLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Checkbox(on, { toggle() }, colors = CheckboxDefaults.colors(checkedColor = c.primary, checkmarkColor = c.onPrimary))
            }
        }
        if (shown.size > limit) {
            Divider()
            Text(
                "Show all ${shown.size} apps", style = MaterialTheme.typography.labelLarge, color = c.foreground,
                modifier = Modifier.fillMaxWidth().clickable { showAll = true }.padding(16.dp),
            )
        }
    }
}
