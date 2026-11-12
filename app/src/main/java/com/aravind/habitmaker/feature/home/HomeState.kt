package com.aravind.habitmaker.feature.home

import com.aravind.habitmaker.App
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.data.db.ExamPlan
import com.aravind.habitmaker.data.db.FocusSession
import com.aravind.habitmaker.data.db.Habit
import com.aravind.habitmaker.data.db.HabitLog
import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.data.usage.DayUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class HomeData(
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val focus: List<FocusSession>,
    val plan: ExamPlan?,
    val routine: List<RoutineBlock>,
)

/** Owned by the app shell so switching tabs retains the last loaded Home values. */
internal class HomeState(private val app: App, private val scope: CoroutineScope) {
    private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000)
    val user = app.auth.state.stateIn(scope, sharing, null)
    val settings = app.prefs.settings.stateIn(scope, sharing, null)
    val data = combine(
        app.db.habits().observeActive(),
        app.db.habitLogs().observeSince(0),
        app.db.focus().observeSince(Dates.range(java.time.LocalDate.now().minusDays(6)).first),
        app.db.routine().observePlan(),
        app.db.routine().observeBlocks(),
    ) { habits, logs, focus, plan, routine -> HomeData(habits, logs, focus, plan, routine) }
        .stateIn(scope, sharing, null)

    private val latestUsage = MutableStateFlow<DayUsage?>(null)
    val usage = latestUsage.asStateFlow()
    private var refreshJob: Job? = null

    fun refreshUsage() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch { latestUsage.value = app.usage.today() }
    }
}
