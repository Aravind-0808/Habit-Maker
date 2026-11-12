package com.aravind.habitmaker.feature.blocking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.LaunchedEffect
import com.aravind.habitmaker.core.util.daysLabel
import com.aravind.habitmaker.data.db.ALL_DAYS
import com.aravind.habitmaker.data.db.BlockSchedule
import com.aravind.habitmaker.navigation.Routes
import com.aravind.habitmaker.ui.components.ConfirmDialog
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.ui.components.SkeletonScreen
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.blocking.BlockPolicy
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.util.formatClock
import com.aravind.habitmaker.core.util.formatDuration
import com.aravind.habitmaker.core.util.formatMinutes
import com.aravind.habitmaker.data.apps.InstalledApp
import com.aravind.habitmaker.data.db.AppRule
import com.aravind.habitmaker.data.db.BlockedSite
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppCard
import com.aravind.habitmaker.ui.components.AppHeader
import com.aravind.habitmaker.ui.components.AppIcon
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.DayPicker
import com.aravind.habitmaker.ui.components.Divider
import com.aravind.habitmaker.ui.components.EmptyState
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.LoadingState
import com.aravind.habitmaker.ui.components.ScrollScreen
import com.aravind.habitmaker.ui.components.SectionLabel
import com.aravind.habitmaker.ui.components.TimeDialog
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

/** Common app-list page: header, search box and a lazy list. */
@Composable
private fun AppListPage(
    title: String,
    onBack: () -> Unit,
    header: @Composable () -> Unit = {},
    sort: (List<InstalledApp>) -> List<InstalledApp> = { it },
    row: @Composable (InstalledApp) -> Unit,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colors
    val apps by produceState<List<InstalledApp>?>(null) { value = context.app.apps.list() }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(c.background)) {
        AppHeader(title, onBack = onBack)
        val list = apps
        if (list == null) { LoadingState(); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header()
                    AppTextField(query, { query = it }, "Search apps", leading = Icons.Filled.Search, imeAction = ImeAction.Search)
                }
            }
            val shown = sort(list).filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            items(shown, key = { it.packageName }) { a ->
                row(a)
                Divider()
            }
        }
    }
}

@Composable
private fun AppLine(app: InstalledApp, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val c = MaterialTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        trailing()
    }
}

/** Tick the apps that count as distracting. */
@Composable
fun DistractingAppsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val loadedRules by remember { app.db.appRules().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val rules = loadedRules ?: run { SkeletonScreen("Distracting apps", onBack); return }

    val byPkg = rules.associateBy { it.packageName }

    fun toggle(pkg: String) = scope.launch {
        val r = byPkg[pkg] ?: AppRule(pkg)
        app.db.appRules().upsert(r.copy(distracting = !r.distracting))
    }

    AppListPage(
        "Distracting apps", onBack,
        header = {
            Text("Blocked during focus and Exam Prep study time, \"Block now\" and your schedules.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            ShortVideoAppsCard()
            SectionLabel("Other apps")
        },
        sort = { list -> list.filter { it.packageName !in SHORT_VIDEO_APPS }.sortedByDescending { byPkg[it.packageName]?.distracting == true } },
    ) { a ->
        val on = byPkg[a.packageName]?.distracting == true
        AppLine(a, null, { toggle(a.packageName) }) {
            Checkbox(on, { toggle(a.packageName) }, colors = CheckboxDefaults.colors(checkedColor = c.primary, checkmarkColor = c.onPrimary))
        }
    }
}

/** Daily time limit per app, sorted by today's use. */
@Composable
fun AppLimitsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val c = MaterialTheme.colors
    val loadedRules by remember { app.db.appRules().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val rules = loadedRules ?: run { SkeletonScreen("App limits", onBack); return }

    val byPkg = rules.associateBy { it.packageName }
    val usage by produceState<Map<String, Long>>(emptyMap()) { value = app.usage.today().byApp.toMap() }
    var editing by remember { mutableStateOf<InstalledApp?>(null) }

    editing?.let { a -> LimitDialog(a.packageName, a.label, byPkg[a.packageName]?.dailyLimitMin ?: 0) { editing = null } }

    AppListPage(
        "App limits", onBack,
        header = {
            if (!app.usage.hasAccess()) InlineBanner("Allow usage access so limits can count your time.", Tone.WARNING)
            Text("Set how long you can use an app each day. It's blocked once the time is used up, until midnight.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        },
        sort = { list -> list.sortedWith(compareByDescending<InstalledApp> { (byPkg[it.packageName]?.dailyLimitMin ?: 0) > 0 }.thenByDescending { usage[it.packageName] ?: 0L }) },
    ) { a ->
        val limit = byPkg[a.packageName]?.dailyLimitMin ?: 0
        val used = usage[a.packageName] ?: 0L
        AppLine(a, "${formatDuration(used)} today", { editing = a }) {
            if (limit > 0) Badge(formatMinutes(limit), if (used >= limit * 60_000L) Tone.DANGER else Tone.INFO)
            else Text("Set", style = MaterialTheme.typography.labelLarge, color = c.muted)
        }
    }
}

/** Pick a daily limit (0 = none) for one app. */
@Composable
fun LimitDialog(pkg: String, label: String, current: Int, onDismiss: () -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    var minutes by remember { mutableIntStateOf(if (current > 0) current else 30) }
    fun save(m: Int) = scope.launch {
        val r = app.db.appRules().get(pkg) ?: AppRule(pkg)
        app.db.appRules().upsert(r.copy(dailyLimitMin = m))
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        icon = { AppIcon(pkg, 40.dp) },
        title = { Text("Daily limit for $label", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineMedium, color = c.foreground)
                Slider(minutes.toFloat(), { minutes = (it / 5).toInt().coerceAtLeast(1) * 5 }, valueRange = 5f..240f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.sunken))
            }
        },
        confirmButton = { TextButton({ save(minutes) }) { Text("Save", color = c.foreground, fontWeight = FontWeight.SemiBold) } },
        dismissButton = {
            Row {
                if (current > 0) TextButton({ save(0) }) { Text("Remove", color = c.danger) }
                TextButton(onDismiss) { Text("Cancel", color = c.muted) }
            }
        },
    )
}

/** All block schedules (e.g. "Study" 09–13, "Sleep" 22–07). */
@Composable
fun ScheduleScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val loadedSchedules by remember { app.db.schedules().observeAll() }.collectAsStateWithLifecycle(initialValue = null)

    val schedules = loadedSchedules ?: run { SkeletonScreen("Schedules", onBack); return }

    ScrollScreen("Schedules", onBack = onBack, actions = {
        IconButton({ go(Routes.scheduleEdit()) }) { Icon(Icons.Filled.Add, "Add schedule", tint = c.foreground) }
    }) {
        Text("Your distracting apps are blocked during every schedule that's switched on.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        if (schedules.isEmpty()) {
            AppCard {
                EmptyState(Icons.Filled.Schedule, "No schedules yet", "Add one for study time, work hours or bedtime.") {
                    AppButton("Add schedule", { go(Routes.scheduleEdit()) }, fullWidth = false, icon = Icons.Filled.Add)
                }
            }
        }
        schedules.forEach { sch ->
            AppCard(onClick = { go(Routes.scheduleEdit(sch.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sch.name, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                        Text("${formatClock(sch.start)}–${formatClock(sch.end)} · ${daysLabel(sch.days)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Switch(sch.enabled, { on -> scope.launch { app.db.schedules().upsert(sch.copy(enabled = on)) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
                }
            }
        }
    }
}

/** Add ([id] = 0) or edit one schedule. */
@Composable
fun ScheduleEditScreen(id: Long, onBack: () -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<BlockSchedule?>(null) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(22 * 60) }
    var end by remember { mutableIntStateOf(7 * 60) }
    var days by remember { mutableIntStateOf(ALL_DAYS) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) app.db.schedules().get(id)?.let { loaded = it; name = it.name; start = it.start; end = it.end; days = it.days }
    }
    when (pick) {
        "start" -> TimeDialog("From", start, { pick = null }) { start = it }
        "end" -> TimeDialog("To", end, { pick = null }) { end = it }
    }
    if (confirmDelete) ConfirmDialog("Delete schedule?", "\"$name\" will be removed.", "Delete",
        { scope.launch { loaded?.let { app.db.schedules().delete(it) }; onBack() } }, { confirmDelete = false })

    ScrollScreen(if (id == 0L) "New schedule" else "Edit schedule", onBack = onBack, actions = {
        if (id != 0L) IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = c.danger) }
    }) {
        AppTextField(name, { name = it }, "Name", placeholder = "e.g. Study, Sleep", error = if (tried && name.isBlank()) "Give it a name" else null)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.weight(1f), onClick = { pick = "start" }) {
                Text("From", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(formatClock(start), style = MaterialTheme.typography.headlineSmall, color = c.foreground)
            }
            AppCard(Modifier.weight(1f), onClick = { pick = "end" }) {
                Text("To", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(formatClock(end), style = MaterialTheme.typography.headlineSmall, color = c.foreground)
            }
        }
        if (start > end) Text("Runs overnight, into the next morning.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (start == end) Text("Start and end are the same — pick different times.", style = MaterialTheme.typography.bodySmall, color = c.danger)
        Column {
            SectionLabel("Days")
            DayPicker(days) { days = it }
            if (tried && days == 0) Text("Pick at least one day", style = MaterialTheme.typography.bodySmall, color = c.danger)
        }
        AppButton("Save", {
            tried = true
            if (name.isBlank() || days == 0 || start == end) return@AppButton
            val sch = (loaded ?: BlockSchedule(name = "", start = 0, end = 0)).copy(name = name.trim(), start = start, end = end, days = days)
            scope.launch { app.db.schedules().upsert(sch); onBack() }
        })
    }
}

/** Domains blocked in common browsers. */
@Composable
fun WebsitesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.app
    val c = MaterialTheme.colors
    val scope = rememberCoroutineScope()
    val s by app.prefs.settings.collectAsStateWithLifecycle(Settings())
    val loadedSites by remember { app.db.sites().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val sites = loadedSites ?: run { SkeletonScreen("Websites", onBack); return }

    ScrollScreen("Websites", onBack = onBack) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Block websites", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    Text("Works in Chrome, Samsung Internet, Firefox, Edge, Brave, Opera and DuckDuckGo.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(s.blockWebsites, { on -> scope.launch { app.prefs.update { it.copy(blockWebsites = on) } } },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary))
            }
        }
        AppTextField(input, { input = it; error = null }, "Add a website", placeholder = "e.g. reddit.com", keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, error = error, leading = Icons.Filled.Language)
        AppButton("Add", {
            val d = BlockPolicy.normalizeDomain(input)
            if (d == null) { error = "Enter a website like reddit.com"; return@AppButton }
            scope.launch { app.db.sites().insert(BlockedSite(d)); input = "" }
        }, kind = ButtonKind.SECONDARY)
        Column {
            SectionLabel("Blocked (${sites.size})")
            if (sites.isEmpty()) {
                AppCard { EmptyState(Icons.Filled.Language, "No websites yet", "Subdomains are blocked too: adding reddit.com also blocks m.reddit.com.") }
            } else {
                AppCard(padding = 0.dp) {
                    sites.forEachIndexed { i, site ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(site.domain, style = MaterialTheme.typography.bodyLarge, color = c.foreground, modifier = Modifier.weight(1f))
                            IconButton({ scope.launch { app.db.sites().delete(site) } }) { Icon(Icons.Filled.Close, "Remove", tint = c.muted) }
                        }
                    }
                }
            }
        }
    }
}
