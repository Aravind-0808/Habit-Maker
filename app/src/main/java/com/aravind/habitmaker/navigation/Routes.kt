package com.aravind.habitmaker.navigation

import android.net.Uri

object Routes {
    // Tabs
    const val HOME = "home"
    const val PREP = "prep"
    const val FOCUS = "focus"
    const val BLOCK = "block"
    const val STATS = "stats"

    /** Pages that show the bottom bar. */
    val TABS = setOf(HOME, PREP, FOCUS, BLOCK, STATS)

    // Inner pages
    const val HABITS = "habits"
    const val PREP_SETUP = "prep/setup"
    const val PREP_CALENDAR = "prep/calendar"
    const val ROUTINE_EDIT = "prep/routine"
    const val ROUTINE_BLOCK = "prep/block/{id}"
    const val ALARM_SETTINGS = "prep/alarm"
    const val PROFILE = "profile"
    const val PERMISSIONS = "permissions"
    const val PREMIUM = "premium"
    const val HABIT_EDIT = "habit/edit/{id}"
    const val HABIT_DETAIL = "habit/{id}"
    const val BLOCK_APPS = "block/apps"
    const val BLOCK_LIMITS = "block/limits"
    const val BLOCK_SCHEDULE = "block/schedule"
    const val BLOCK_SITES = "block/sites"
    const val SCHEDULE_EDIT = "block/schedule/{id}"
    const val APP_USAGE = "usage/{pkg}"
    const val INFO = "info/{kind}"

    fun habitEdit(id: Long = 0) = "habit/edit/$id"
    fun habitDetail(id: Long) = "habit/$id"
    fun appUsage(pkg: String) = "usage/${Uri.encode(pkg)}"
    fun info(kind: String) = "info/$kind"
    fun scheduleEdit(id: Long = 0) = "block/schedule/$id"
    fun routineBlock(id: Long = 0) = "prep/block/$id"
}
