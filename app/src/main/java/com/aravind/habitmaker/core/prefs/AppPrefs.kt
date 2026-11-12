package com.aravind.habitmaker.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aravind.habitmaker.ui.theme.Accent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class Goal(val label: String) { STUDY("Study better"), WORK("Focus at work"), LESS_PHONE("Use my phone less"), SLEEP("Sleep better") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.ZINC,
    val secondaryAccent: Accent = Accent.VIOLET,
    val primaryColor: Long? = null,
    val secondaryColor: Long? = null,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val onboardingDone: Boolean = false,
    val permissionsSeen: Boolean = false,
    val goal: Goal = Goal.LESS_PHONE,
    /** Daily screen-time target, minutes. */
    val dailyTargetMin: Int = 180,
    val defaultFocusMin: Int = 25,
    val breakMin: Int = 5,
    val blockDuringFocus: Boolean = true,
    val remindersEnabled: Boolean = true,
    val blockShorts: Boolean = false,
    val blockWebsites: Boolean = true,
    /** "Block now": distracting apps are blocked until this time (ms). */
    val blockNowUntil: Long = 0,
    /** Strict: a running "block now" or focus session cannot be stopped early. */
    val strict: Boolean = false,
    val overlayMessage: String = "Is this what you planned to do right now?",
    /** Alarm sound URI; empty = the phone's default alarm. */
    val alarmSound: String = "",
    val alarmSoundName: String = "Default alarm",
    val alarmVibrate: Boolean = true,
    val alarmFlash: Boolean = false,
    val alarmFlashMode: FlashMode = FlashMode.FAST,
    val alarmSnoozeMin: Int = 5,
    /** Volume starts low and rises over 30 s. */
    val alarmRamp: Boolean = true,
)

enum class TimeFormat(val label: String) { SYSTEM("Phone"), H12("12-hour"), H24("24-hour") }

enum class FlashMode(val label: String) { FAST("Fast"), SLOW("Slow"), SOS("SOS") }

private val Context.store by preferencesDataStore("habitmaker.app")

/** Device-only preferences. */
class AppPrefs(private val context: Context) {
    private val ds get() = context.store

    val settings: Flow<Settings> = ds.data.map { it.toSettings() }
    val theme: Flow<ThemeMode> = settings.map { it.theme }

    suspend fun current(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        ds.edit { p -> p.write(change(p.toSettings())) }
    }

    suspend fun setTheme(mode: ThemeMode) = update { it.copy(theme = mode) }

    private fun Preferences.toSettings() = Settings(
        theme = this[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        accent = this[ACCENT]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.ZINC,
        secondaryAccent = this[SECONDARY_ACCENT]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.VIOLET,
        primaryColor = this[PRIMARY_COLOR],
        secondaryColor = this[SECONDARY_COLOR],
        timeFormat = this[TIME_FORMAT]?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() } ?: TimeFormat.SYSTEM,
        onboardingDone = this[ONBOARDING_DONE] ?: false,
        permissionsSeen = this[PERMISSIONS_SEEN] ?: false,
        goal = this[GOAL]?.let { runCatching { Goal.valueOf(it) }.getOrNull() } ?: Goal.LESS_PHONE,
        dailyTargetMin = this[DAILY_TARGET] ?: 180,
        defaultFocusMin = this[FOCUS_MIN] ?: 25,
        breakMin = this[BREAK_MIN] ?: 5,
        blockDuringFocus = this[BLOCK_FOCUS] ?: true,
        remindersEnabled = this[REMINDERS] ?: true,
        blockShorts = this[BLOCK_SHORTS] ?: false,
        blockWebsites = this[BLOCK_WEBSITES] ?: true,
        blockNowUntil = this[BLOCK_NOW_UNTIL] ?: 0,
        strict = this[STRICT] ?: false,
        overlayMessage = this[OVERLAY_MSG] ?: Settings().overlayMessage,
        alarmSound = this[ALARM_SOUND] ?: "",
        alarmSoundName = this[ALARM_SOUND_NAME] ?: "Default alarm",
        alarmVibrate = this[ALARM_VIBRATE] ?: true,
        alarmFlash = this[ALARM_FLASH] ?: false,
        alarmFlashMode = this[ALARM_FLASH_MODE]?.let { runCatching { FlashMode.valueOf(it) }.getOrNull() } ?: FlashMode.FAST,
        alarmSnoozeMin = this[ALARM_SNOOZE] ?: 5,
        alarmRamp = this[ALARM_RAMP] ?: true,
    )

    private fun MutablePreferences.write(s: Settings) {
        this[THEME] = s.theme.name
        this[ACCENT] = s.accent.name
        this[SECONDARY_ACCENT] = s.secondaryAccent.name
        if (s.primaryColor == null) remove(PRIMARY_COLOR) else this[PRIMARY_COLOR] = s.primaryColor
        if (s.secondaryColor == null) remove(SECONDARY_COLOR) else this[SECONDARY_COLOR] = s.secondaryColor
        this[TIME_FORMAT] = s.timeFormat.name
        this[ONBOARDING_DONE] = s.onboardingDone
        this[PERMISSIONS_SEEN] = s.permissionsSeen
        this[GOAL] = s.goal.name
        this[DAILY_TARGET] = s.dailyTargetMin
        this[FOCUS_MIN] = s.defaultFocusMin
        this[BREAK_MIN] = s.breakMin
        this[BLOCK_FOCUS] = s.blockDuringFocus
        this[REMINDERS] = s.remindersEnabled
        this[BLOCK_SHORTS] = s.blockShorts
        this[BLOCK_WEBSITES] = s.blockWebsites
        this[BLOCK_NOW_UNTIL] = s.blockNowUntil
        this[STRICT] = s.strict
        this[OVERLAY_MSG] = s.overlayMessage
        this[ALARM_SOUND] = s.alarmSound
        this[ALARM_SOUND_NAME] = s.alarmSoundName
        this[ALARM_VIBRATE] = s.alarmVibrate
        this[ALARM_FLASH] = s.alarmFlash
        this[ALARM_FLASH_MODE] = s.alarmFlashMode.name
        this[ALARM_SNOOZE] = s.alarmSnoozeMin
        this[ALARM_RAMP] = s.alarmRamp
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val SECONDARY_ACCENT = stringPreferencesKey("secondary_accent")
        val PRIMARY_COLOR = longPreferencesKey("primary_color")
        val SECONDARY_COLOR = longPreferencesKey("secondary_color")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PERMISSIONS_SEEN = booleanPreferencesKey("permissions_seen")
        val GOAL = stringPreferencesKey("goal")
        val DAILY_TARGET = intPreferencesKey("daily_target_min")
        val FOCUS_MIN = intPreferencesKey("focus_min")
        val BREAK_MIN = intPreferencesKey("break_min")
        val BLOCK_FOCUS = booleanPreferencesKey("block_during_focus")
        val REMINDERS = booleanPreferencesKey("reminders")
        val BLOCK_SHORTS = booleanPreferencesKey("block_shorts")
        val BLOCK_WEBSITES = booleanPreferencesKey("block_websites")
        val BLOCK_NOW_UNTIL = longPreferencesKey("block_now_until")
        val STRICT = booleanPreferencesKey("strict")
        val OVERLAY_MSG = stringPreferencesKey("overlay_message")
        val ALARM_SOUND = stringPreferencesKey("alarm_sound")
        val ALARM_SOUND_NAME = stringPreferencesKey("alarm_sound_name")
        val ALARM_VIBRATE = booleanPreferencesKey("alarm_vibrate")
        val ALARM_FLASH = booleanPreferencesKey("alarm_flash")
        val ALARM_FLASH_MODE = stringPreferencesKey("alarm_flash_mode")
        val ALARM_SNOOZE = intPreferencesKey("alarm_snooze")
        val ALARM_RAMP = booleanPreferencesKey("alarm_ramp")
    }
}
