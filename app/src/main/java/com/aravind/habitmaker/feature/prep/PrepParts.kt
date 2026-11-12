package com.aravind.habitmaker.feature.prep

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.routine.BlockType
import com.aravind.habitmaker.core.routine.DayResult
import com.aravind.habitmaker.core.routine.DayStatus
import com.aravind.habitmaker.core.routine.Draft
import com.aravind.habitmaker.core.routine.RoutineAlarms
import com.aravind.habitmaker.core.routine.RoutineGenerator
import com.aravind.habitmaker.core.routine.contains
import com.aravind.habitmaker.core.routine.kind
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.data.db.ExamPlan
import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.data.db.RoutineLog
import com.aravind.habitmaker.ui.theme.Blue
import com.aravind.habitmaker.ui.theme.Emerald
import com.aravind.habitmaker.ui.theme.Violet
import com.aravind.habitmaker.ui.theme.colors
import java.time.LocalDate

/** Writes for the routine, shared by every Prep page. */
object RoutineActions {
    /** Saves the plan and replaces the routine with [drafts]. Done/missed history is kept. */
    suspend fun applyPlan(context: Context, plan: ExamPlan, drafts: List<Draft>) {
        val dao = context.app.db.routine()
        dao.blocks().forEach { RoutineAlarms.cancel(context, it.id) }
        dao.savePlan(plan)
        dao.clearBlocks()
        dao.insertBlocks(RoutineGenerator.toBlocks(drafts))
        RoutineAlarms.scheduleAll(context)
    }

    suspend fun saveBlock(context: Context, block: RoutineBlock) {
        val dao = context.app.db.routine()
        val id = if (block.id == 0L) dao.upsertBlock(block) else { dao.upsertBlock(block); block.id }
        RoutineAlarms.schedule(context, block.copy(id = id))
    }

    suspend fun deleteBlock(context: Context, block: RoutineBlock) {
        RoutineAlarms.cancel(context, block.id)
        context.app.db.routine().deleteBlock(block)
    }

    /** [status] null clears the mark. */
    suspend fun mark(context: Context, blockId: Long, day: Long, status: Int?) {
        val dao = context.app.db.routine()
        if (status == null) dao.deleteLog(day, blockId) else dao.upsertLog(RoutineLog(day, blockId, status))
    }

    suspend fun reset(context: Context) {
        val dao = context.app.db.routine()
        dao.blocks().forEach { RoutineAlarms.cancel(context, it.id) }
        dao.clearBlocks(); dao.clearLogs(); dao.clearPlan()
    }
}

/** The day's blocks in time order (sleep last). */
fun List<RoutineBlock>.on(day: LocalDate): List<RoutineBlock> =
    filter { it.days and Dates.bit(day.dayOfWeek) != 0 }
        .sortedWith(compareBy<RoutineBlock> { it.kind == BlockType.SLEEP }.thenBy { it.start })

fun List<RoutineBlock>.current(minuteOfDay: Int): RoutineBlock? =
    firstOrNull { it.kind != BlockType.SLEEP && it.contains(minuteOfDay) } ?: firstOrNull { it.contains(minuteOfDay) }

fun List<RoutineBlock>.next(minuteOfDay: Int): RoutineBlock? =
    filter { it.kind != BlockType.SLEEP && it.start > minuteOfDay }.minByOrNull { it.start }

/** Done / missed for every day from [from] to [to], using today's routine. */
fun dayStatuses(blocks: List<RoutineBlock>, logs: List<RoutineLog>, from: LocalDate, to: LocalDate): Map<LocalDate, DayStatus> {
    val today = LocalDate.now()
    val all = blocks.map { it.id }.toSet()
    // Nothing before the first day anything was marked: the plan wasn't running yet.
    val first = logs.minOfOrNull { it.day }?.let { LocalDate.ofEpochDay(it) } ?: today
    val byDay = logs.groupBy { it.day }
    val out = LinkedHashMap<LocalDate, DayStatus>()
    var d = from
    while (!d.isAfter(to)) {
        val study = blocks.on(d).filter { it.kind.isStudy }.map { it.id }
        out[d] = if (d.isBefore(first)) DayStatus.NONE
        else DayResult.status(study, all, byDay[d.toEpochDay()].orEmpty(), isToday = d == today, isFuture = d.isAfter(today))
        d = d.plusDays(1)
    }
    return out
}

fun BlockType.icon(): ImageVector = when (this) {
    BlockType.WAKE -> Icons.Filled.Alarm
    BlockType.REFRESH -> Icons.Filled.Shower
    BlockType.STUDY -> Icons.Filled.School
    BlockType.REVISION -> Icons.AutoMirrored.Filled.MenuBook
    BlockType.MOCK_TEST -> Icons.Filled.Quiz
    BlockType.BREAK -> Icons.Filled.Coffee
    BlockType.MEAL -> Icons.Filled.Restaurant
    BlockType.EXERCISE -> Icons.Filled.FitnessCenter
    BlockType.WORK -> Icons.Filled.Work
    BlockType.SLEEP -> Icons.Filled.Bedtime
    BlockType.CUSTOM -> Icons.Filled.EditNote
}

@Composable
fun BlockType.tint(): Color {
    val c = MaterialTheme.colors
    return c.primary
}
