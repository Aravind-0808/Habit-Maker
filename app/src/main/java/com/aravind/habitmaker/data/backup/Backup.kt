package com.aravind.habitmaker.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.reminders.HabitReminders
import com.aravind.habitmaker.data.db.AppRule
import com.aravind.habitmaker.data.db.BlockSchedule
import com.aravind.habitmaker.data.db.ExamPlan
import com.aravind.habitmaker.data.db.RoutineBlock
import com.aravind.habitmaker.data.db.RoutineLog
import com.aravind.habitmaker.core.routine.RoutineAlarms
import com.aravind.habitmaker.data.db.BlockedSite
import com.aravind.habitmaker.data.db.FocusSession
import com.aravind.habitmaker.data.db.Habit
import com.aravind.habitmaker.data.db.HabitLog
import com.aravind.habitmaker.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Everything in the database as one JSON file the user saves wherever they like. */
object Backup {
    private const val VERSION = 1

    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = context.app.db
        val json = JSONObject()
            .put("app", "habitmaker").put("version", VERSION).put("exportedAt", System.currentTimeMillis())
            .put("habits", JSONArray(db.habits().all().map { h ->
                JSONObject().put("id", h.id).put("name", h.name).put("icon", h.icon).put("color", h.color).put("days", h.days)
                    .put("reminderMinute", h.reminderMinute ?: JSONObject.NULL).put("target", h.target)
                    .put("createdAt", h.createdAt).put("archived", h.archived)
            }))
            .put("logs", JSONArray(db.habitLogs().all().map { JSONObject().put("habitId", it.habitId).put("day", it.day).put("count", it.count) }))
            .put("focus", JSONArray(db.focus().all().map {
                JSONObject().put("start", it.start).put("end", it.end).put("plannedMin", it.plannedMin)
                    .put("actualMin", it.actualMin).put("completed", it.completed).put("label", it.label)
            }))
            .put("rules", JSONArray(db.appRules().all().map { JSONObject().put("pkg", it.packageName).put("distracting", it.distracting).put("limit", it.dailyLimitMin).put("shortsOnly", it.shortsOnly) }))
            .put("sites", JSONArray(db.sites().all().map { it.domain }))
            .put("examPlan", db.routine().plan()?.let { p ->
                JSONObject().put("exam", p.exam).put("examDay", p.examDay ?: JSONObject.NULL).put("profile", p.profile)
                    .put("wake", p.wake).put("sleep", p.sleep).put("workStart", p.workStart).put("workEnd", p.workEnd)
                    .put("studyMin", p.studyMin).put("sessionMin", p.sessionMin).put("breakMin", p.breakMin)
                    .put("autoFocus", p.autoFocus).put("strictFocus", p.strictFocus)
            } ?: JSONObject.NULL)
            .put("routine", JSONArray(db.routine().blocks().map {
                JSONObject().put("id", it.id).put("type", it.type).put("title", it.title).put("start", it.start)
                    .put("end", it.end).put("days", it.days).put("remind", it.remind)
            }))
            .put("routineLogs", JSONArray(db.routine().logs().map { JSONObject().put("day", it.day).put("blockId", it.blockId).put("status", it.status) }))
            .put("schedules", JSONArray(db.schedules().all().map {
                JSONObject().put("name", it.name).put("start", it.start).put("end", it.end).put("days", it.days).put("enabled", it.enabled)
            }))
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toString(2).toByteArray()) }
    }

    /** Replaces all data with the file's. Throws if it isn't a Habit Maker backup. */
    suspend fun import(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val json = JSONObject(text)
        require(json.optString("app") == "habitmaker") { "This file is not a Habit Maker backup." }
        val db = context.app.db
        db.habits().all().forEach { HabitReminders.cancel(context, it.id) }
        db.routine().blocks().forEach { RoutineAlarms.cancel(context, it.id) }
        db.withTransaction {
            db.habitLogs().clear(); db.habits().clear(); db.focus().clear(); db.appRules().clear(); db.sites().clear(); db.schedules().clear()
            db.routine().clearBlocks(); db.routine().clearLogs(); db.routine().clearPlan()
            json.getJSONArray("habits").objects().forEach { o ->
                db.habits().upsert(Habit(
                    id = o.getLong("id"), name = o.getString("name"), icon = o.getString("icon"), color = o.getLong("color"),
                    days = o.getInt("days"), reminderMinute = if (o.isNull("reminderMinute")) null else o.getInt("reminderMinute"),
                    target = o.getInt("target"), createdAt = o.getLong("createdAt"), archived = o.getBoolean("archived"),
                ))
            }
            json.getJSONArray("logs").objects().forEach { db.habitLogs().upsert(HabitLog(it.getLong("habitId"), it.getLong("day"), it.getInt("count"))) }
            json.getJSONArray("focus").objects().forEach {
                db.focus().insert(FocusSession(start = it.getLong("start"), end = it.getLong("end"), plannedMin = it.getInt("plannedMin"),
                    actualMin = it.getInt("actualMin"), completed = it.getBoolean("completed"), label = it.getString("label")))
            }
            json.getJSONArray("rules").objects().forEach { db.appRules().upsert(AppRule(it.getString("pkg"), it.getBoolean("distracting"), it.getInt("limit"), it.optBoolean("shortsOnly"))) }
            val sites = json.getJSONArray("sites")
            for (i in 0 until sites.length()) db.sites().insert(BlockedSite(sites.getString(i)))
            json.optJSONObject("examPlan")?.let { p ->
                db.routine().savePlan(ExamPlan(
                    exam = p.getString("exam"), examDay = if (p.isNull("examDay")) null else p.getLong("examDay"), profile = p.getString("profile"),
                    wake = p.getInt("wake"), sleep = p.getInt("sleep"), workStart = p.getInt("workStart"), workEnd = p.getInt("workEnd"),
                    studyMin = p.getInt("studyMin"), sessionMin = p.getInt("sessionMin"), breakMin = p.getInt("breakMin"),
                    autoFocus = p.getBoolean("autoFocus"), strictFocus = p.getBoolean("strictFocus"),
                ))
            }
            json.optJSONArray("routine")?.objects()?.forEach {
                db.routine().upsertBlock(RoutineBlock(it.getLong("id"), it.getString("type"), it.getString("title"), it.getInt("start"), it.getInt("end"), it.getInt("days"), it.getBoolean("remind")))
            }
            json.optJSONArray("routineLogs")?.objects()?.forEach { db.routine().upsertLog(RoutineLog(it.getLong("day"), it.getLong("blockId"), it.getInt("status"))) }
            json.optJSONArray("schedules")?.objects()?.forEach {
                db.schedules().upsert(BlockSchedule(name = it.getString("name"), start = it.getInt("start"), end = it.getInt("end"), days = it.getInt("days"), enabled = it.getBoolean("enabled")))
            }
        }
        HabitReminders.rescheduleAll(context)
        RoutineAlarms.scheduleAll(context)
        Widgets.refresh(context)
    }

    /** Wipes every table (used by "Delete all data"). */
    suspend fun wipe(context: Context) = withContext(Dispatchers.IO) {
        val db = context.app.db
        db.habits().all().forEach { HabitReminders.cancel(context, it.id) }
        db.routine().blocks().forEach { RoutineAlarms.cancel(context, it.id) }
        db.clearAllTables()
        Widgets.refresh(context)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
