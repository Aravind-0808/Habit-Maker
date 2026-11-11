package com.aravind.habitmaker.service

import android.accessibilityservice.AccessibilityService
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.blocking.BlockPolicy
import com.aravind.habitmaker.core.blocking.BlockReason
import com.aravind.habitmaker.core.blocking.BlockerStatus
import com.aravind.habitmaker.core.blocking.PolicyInput
import com.aravind.habitmaker.core.blocking.ScheduleWindow
import com.aravind.habitmaker.core.focus.FocusTimer
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.data.db.AppRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Watches which app (and, for a few apps, which page) is on screen and shows
 * the block pop-up over it when a rule says so. The app is not closed until
 * the user chooses. All decisions are in [BlockPolicy]; this class only
 * gathers inputs and acts.
 */
class BlockerService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var rules: Map<String, AppRule> = emptyMap()
    @Volatile private var sites: List<String> = emptyList()
    @Volatile private var schedules: List<ScheduleWindow> = emptyList()
    @Volatile private var settings = Settings()
    @Volatile private var usageToday: Map<String, Long> = emptyMap()
    @Volatile private var usageAt = 0L

    private val prompt by lazy { BlockPrompt(this) }
    /** "5 more minutes": snooze key → until (ms). */
    private val snoozedUntil = HashMap<String, Long>()
    /** Keyboards: their windows must not close the pop-up. */
    private val keyboards: Set<String> by lazy {
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
    }

    private var lastBlockKey = ""
    private var lastBlockAt = 0L
    private var lastContentCheck = 0L

    override fun onServiceConnected() {
        val app = app
        BlockerStatus.running(true)
        // When focus, "block now" or a rule changes, check the app that is already open.
        scope.launch { FocusTimer.run.collect { checkCurrent() } }
        scope.launch { app.db.appRules().observeAll().collect { list -> rules = list.associateBy { it.packageName }; checkCurrent() } }
        scope.launch { app.db.sites().observeAll().collect { list -> sites = list.map { it.domain } } }
        scope.launch {
            app.db.schedules().observeAll().collect { list ->
                schedules = list.filter { it.enabled }.map { ScheduleWindow(it.start, it.end, it.days) }
                checkCurrent()
            }
        }
        // Schedules start on the clock, not on an event: re-check every minute.
        scope.launch { while (isActive) { delay(60_000); checkCurrent() } }
        scope.launch { app.prefs.settings.collect { settings = it; checkCurrent() } }
        scope.launch {
            while (isActive) {
                if (rules.values.any { it.dailyLimitMin > 0 }) refreshUsage()
                delay(60_000)
            }
        }
    }

    /** Checks the app in front right now (not waiting for the user to switch apps). */
    private fun checkCurrent() {
        if (prompt.showing) return
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (!checkApp(pkg) && shortsBlocked(pkg) && onShortsPage(pkg)) block(pkg, BlockReason.SHORTS)
    }

    private suspend fun refreshUsage() {
        usageToday = app.usage.today().byApp.toMap()
        usageAt = SystemClock.elapsedRealtime()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // our own pop-up
        val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (prompt.showing) {
            // The user left the blocked app some other way (home gesture, recents…): drop the pop-up.
            if (windowChanged && pkg != prompt.shownFor && pkg != "com.android.systemui" && pkg !in keyboards) {
                prompt.hide()
                releaseMedia()
            }
            else return
        }
        if (pkg == "com.android.systemui") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            BlockerStatus.sawApp(pkg)
            val rule = rules[pkg]
            if (rule != null && rule.dailyLimitMin > 0 && SystemClock.elapsedRealtime() - usageAt > 30_000) {
                scope.launch { refreshUsage(); checkApp(pkg) }
            }
            if (checkApp(pkg)) return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastContentCheck < 400) return
        lastContentCheck = now
        if (checkApp(pkg)) return

        val s = settings
        if (pkg in SETTINGS_APPS && strictActive() && showsHabitMaker()) {
            block(pkg, BlockReason.PROTECT)
            return
        }
        if (shortsBlocked(pkg) && onShortsPage(pkg)) {
            block(pkg, BlockReason.SHORTS)
            return
        }
        if (s.blockWebsites && sites.isNotEmpty()) {
            val barId = URL_BARS[pkg] ?: return
            val host = currentHost(barId) ?: return
            if (sites.any { BlockPolicy.matchesSite(host, it) }) block(pkg, BlockReason.WEBSITE, detail = host)
        }
    }

    /** Blocks the whole app if a rule says so. */
    private fun policyInput(): PolicyInput {
        val s = settings
        val now = LocalDateTime.now()
        return PolicyInput(
            rules = rules,
            focusRunning = FocusTimer.running,
            blockDuringFocus = s.blockDuringFocus,
            blockNowUntil = s.blockNowUntil,
            schedules = schedules,
            nowMs = System.currentTimeMillis(),
            minuteOfDay = now.hour * 60 + now.minute,
            weekday = now.dayOfWeek.value - 1,
        )
    }

    private fun checkApp(pkg: String): Boolean {
        val reason = BlockPolicy.decide(pkg, usageToday[pkg] ?: 0L, policyInput()) ?: return false
        return block(pkg, reason)
    }

    private fun shortsBlocked(pkg: String) = pkg in SHORTS_VIEWS && BlockPolicy.blockShortsNow(pkg, settings.blockShorts, policyInput())

    /**
     * Two checks, because apps change their screens often:
     * 1. a view id that only exists in the short-video player, or
     * 2. the bottom "Shorts"/"Reels" tab is the selected one.
     */
    private fun onShortsPage(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        if (SHORTS_VIEWS[pkg].orEmpty().any { id -> root.findAccessibilityNodeInfosByViewId(id).isNotEmpty() }) return true
        val tab = SHORTS_TABS[pkg] ?: return false
        return root.findAccessibilityNodeInfosByText(tab).any { node ->
            val label = (node.contentDescription ?: node.text)?.toString()?.trim().orEmpty()
            label.startsWith(tab, ignoreCase = true) && (node.isSelected || node.parent?.isSelected == true)
        }
    }

    private val audio by lazy { getSystemService(AudioManager::class.java) }
    /** AudioFocusRequest on Android 8+, a marker on older phones. */
    private var focusHeld: Any? = null

    /** Takes audio focus, which makes video apps (YouTube, Instagram…) pause. */
    @Suppress("DEPRECATION")
    private fun pauseMedia() {
        if (focusHeld != null) return
        runCatching {
            focusHeld = if (Build.VERSION.SDK_INT >= 26) {
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .build()
                    .also { audio.requestAudioFocus(it) }
            } else {
                audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                Unit
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseMedia() {
        val held = focusHeld ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26 && held is AudioFocusRequest) audio.abandonAudioFocusRequest(held)
            else audio.abandonAudioFocus(null)
        }
        focusHeld = null
    }

    /** A strict focus session or strict "block now" is running. */
    private fun strictActive(): Boolean =
        FocusTimer.run.value?.strict == true || (settings.strict && settings.blockNowUntil > System.currentTimeMillis())

    /** Settings / uninstall dialog is showing Habit Maker (its app info, accessibility switch, uninstall prompt). */
    private fun showsHabitMaker(): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(APP_NAME).isNotEmpty()
    }

    /** The site in the browser's address bar, unless the user is typing in it. */
    private fun currentHost(barId: String): String? {
        val root = rootInActiveWindow ?: return null
        val bar = root.findAccessibilityNodeInfosByViewId(barId).firstOrNull() ?: return null
        if (bar.isFocused) return null
        return BlockPolicy.hostOf(bar.text?.toString() ?: return null)
    }

    /** Shows the pop-up over [pkg]. False if the user snoozed this block. */
    private fun block(pkg: String, reason: BlockReason, detail: String? = null): Boolean {
        val inApp = reason == BlockReason.SHORTS || reason == BlockReason.WEBSITE || reason == BlockReason.PROTECT
        val snoozeKey = if (inApp) "$pkg/$reason" else "$pkg/app"
        if ((snoozedUntil[snoozeKey] ?: 0L) > System.currentTimeMillis()) return false
        if (prompt.showing) return true

        val key = "$pkg/$reason"
        val now = SystemClock.elapsedRealtime()
        if (key == lastBlockKey && now - lastBlockAt < 1500) return true
        lastBlockKey = key
        lastBlockAt = now
        BlockerStatus.blocked(pkg, reason)

        val s = settings
        val strict = when (reason) {
            BlockReason.FOCUS -> FocusTimer.run.value?.strict == true
            BlockReason.BLOCK_NOW -> s.strict
            BlockReason.PROTECT -> true
            else -> false
        }
        // Stop the video / sound under the pop-up. For Shorts & Reels also leave the
        // player right away, so nothing keeps playing behind it.
        pauseMedia()
        val leftAlready = reason == BlockReason.SHORTS
        if (leftAlready) performGlobalAction(GLOBAL_ACTION_BACK)

        val show = { prompt.show(
            PromptInfo(pkg, app.apps.label(pkg), reason, detail, s.overlayMessage, strict, inApp, s.theme, s.accent),
            onLeave = {
                prompt.hide()
                lastBlockAt = SystemClock.elapsedRealtime()
                if (!leftAlready) performGlobalAction(if (inApp) GLOBAL_ACTION_BACK else GLOBAL_ACTION_HOME)
                // Give the app time to go to the background before other audio may resume.
                scope.launch { delay(1500); releaseMedia() }
            },
            onSnooze = {
                snoozedUntil[snoozeKey] = System.currentTimeMillis() + SNOOZE_MS
                prompt.hide()
                releaseMedia()
            },
        ) }
        // After an automatic "back", wait for it to finish before the pop-up takes the keys.
        if (leftAlready) scope.launch { delay(400); show() } else show()
        return true
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        prompt.hide()
        releaseMedia()
        BlockerStatus.running(false)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SNOOZE_MS = 5 * 60_000L
        const val APP_NAME = "Habit Maker"

        /** Where the app can be uninstalled or the blocker switched off. */
        val SETTINGS_APPS = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
        )

        /** View ids that are only on screen in each app's short-video player. */
        val SHORTS_VIEWS = mapOf(
            "com.google.android.youtube" to listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_player",
                "com.google.android.youtube:id/shorts_container",
            ),
            "com.instagram.android" to listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_tab",
                "com.instagram.android:id/clips_video_container",
            ),
        )

        /** Bottom-tab label of the short-video section (English UI). */
        val SHORTS_TABS = mapOf(
            "com.google.android.youtube" to "Shorts",
            "com.instagram.android" to "Reels",
        )

        /** Address-bar view ids of common browsers. */
        val URL_BARS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.chrome.beta" to "com.chrome.beta:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.opera.browser" to "com.opera.browser:id/url_field",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
        )
    }
}
