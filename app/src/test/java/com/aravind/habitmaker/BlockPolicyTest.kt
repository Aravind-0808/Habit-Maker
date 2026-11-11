package com.aravind.habitmaker

import com.aravind.habitmaker.core.blocking.BlockPolicy
import com.aravind.habitmaker.core.blocking.BlockReason
import com.aravind.habitmaker.core.blocking.PolicyInput
import com.aravind.habitmaker.core.blocking.ScheduleWindow
import com.aravind.habitmaker.data.db.ALL_DAYS
import com.aravind.habitmaker.data.db.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {
    private val ig = "com.instagram.android"
    private fun input(
        rules: List<AppRule> = listOf(AppRule(ig, distracting = true)),
        focus: Boolean = false, blockDuringFocus: Boolean = true, blockNowUntil: Long = 0,
        schedules: List<ScheduleWindow> = emptyList(),
        minute: Int = 12 * 60, weekday: Int = 0,
    ) = PolicyInput(rules.associateBy { it.packageName }, focus, blockDuringFocus, blockNowUntil, schedules, 1_000L, minute, weekday)

    private val night = ScheduleWindow(22 * 60, 7 * 60, ALL_DAYS)
    private fun active(w: ScheduleWindow, minute: Int, weekday: Int = 0) = BlockPolicy.scheduleActive(w, minute, weekday)

    @Test fun unknownAppIsNeverBlocked() = assertNull(BlockPolicy.decide("x.y", 0, input(focus = true)))
    @Test fun focusBlocksDistracting() = assertEquals(BlockReason.FOCUS, BlockPolicy.decide(ig, 0, input(focus = true)))
    @Test fun focusWithoutBlockingSettingDoesNot() = assertNull(BlockPolicy.decide(ig, 0, input(focus = true, blockDuringFocus = false)))
    @Test fun blockNowActive() = assertEquals(BlockReason.BLOCK_NOW, BlockPolicy.decide(ig, 0, input(blockNowUntil = 5_000)))
    @Test fun blockNowExpired() = assertNull(BlockPolicy.decide(ig, 0, input(blockNowUntil = 500)))

    @Test fun limitReached() {
        val r = listOf(AppRule(ig, dailyLimitMin = 30))
        assertNull(BlockPolicy.decide(ig, 29 * 60_000L, input(rules = r)))
        assertEquals(BlockReason.LIMIT, BlockPolicy.decide(ig, 30 * 60_000L, input(rules = r)))
    }

    @Test fun overnightScheduleLateAndEarly() {
        assertTrue(active(night, 23 * 60))
        assertTrue(active(night, 6 * 60))
        assertFalse(active(night, 12 * 60))
    }

    @Test fun overnightEarlyPartBelongsToPreviousDay() {
        // Only Monday on (bit 0). Tuesday 06:00 is Monday night's window; Monday 06:00 is Sunday's.
        val mondayNight = night.copy(days = 1)
        assertTrue(active(mondayNight, 6 * 60, weekday = 1))
        assertFalse(active(mondayNight, 6 * 60, weekday = 0))
    }

    @Test fun daytimeSchedule() {
        val work = ScheduleWindow(9 * 60, 17 * 60, ALL_DAYS)
        assertTrue(active(work, 9 * 60))
        assertFalse(active(work, 17 * 60))
        assertEquals(BlockReason.SCHEDULE, BlockPolicy.decide(ig, 0, input(schedules = listOf(work))))
    }

    @Test fun anyOfSeveralSchedulesBlocks() {
        val study = ScheduleWindow(9 * 60, 11 * 60, ALL_DAYS)
        val evening = ScheduleWindow(18 * 60, 20 * 60, ALL_DAYS)
        assertNull(BlockPolicy.decide(ig, 0, input(schedules = listOf(study, evening), minute = 12 * 60)))
        assertEquals(BlockReason.SCHEDULE, BlockPolicy.decide(ig, 0, input(schedules = listOf(study, evening), minute = 19 * 60)))
    }

    @Test fun shortsOnlyBlocksReelsNotTheApp() {
        val r = listOf(AppRule(ig, shortsOnly = true))
        // Studying: the app opens, but Reels are blocked
        assertNull(BlockPolicy.decide(ig, 0, input(rules = r, focus = true)))
        assertTrue(BlockPolicy.blockShortsNow(ig, alwaysBlockShorts = false, input = input(rules = r, focus = true)))
        // Not studying: Reels allowed
        assertFalse(BlockPolicy.blockShortsNow(ig, alwaysBlockShorts = false, input = input(rules = r)))
        // "Always block" switch wins
        assertTrue(BlockPolicy.blockShortsNow(ig, alwaysBlockShorts = true, input = input(rules = emptyList())))
    }

    @Test fun siteMatching() {
        assertTrue(BlockPolicy.matchesSite("www.reddit.com", "reddit.com"))
        assertTrue(BlockPolicy.matchesSite("m.reddit.com", "reddit.com"))
        assertFalse(BlockPolicy.matchesSite("notreddit.com", "reddit.com"))
    }

    @Test fun hostParsing() {
        assertEquals("m.youtube.com", BlockPolicy.hostOf("https://m.youtube.com/watch?v=1"))
        assertEquals("reddit.com", BlockPolicy.hostOf("reddit.com/r/android"))
        assertNull(BlockPolicy.hostOf("search words"))
        assertEquals("reddit.com", BlockPolicy.normalizeDomain("https://www.Reddit.com/r/x"))
    }
}
