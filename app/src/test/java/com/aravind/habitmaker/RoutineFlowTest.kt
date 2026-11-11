package com.aravind.habitmaker

import com.aravind.habitmaker.core.routine.BlockType
import com.aravind.habitmaker.core.routine.RoutineFlow
import com.aravind.habitmaker.data.db.RoutineBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineFlowTest {
    private fun block(id: Long, type: BlockType, start: Int, end: Int) = RoutineBlock(id, type.name, type.label, start, end)
    private val breakBlock = block(2, BlockType.BREAK, 6 * 60 + 45, 7 * 60)
    private val study2 = block(3, BlockType.STUDY, 7 * 60, 7 * 60 + 45)

    @Test fun studyFinished_goesToBreak() {
        val next = RoutineFlow.next(afterBreak = false, b = breakBlock, minute = 6 * 60 + 45, autoFocus = false, strict = false, nowMs = 0)!!
        assertTrue(next.isBreak)
        assertEquals(15, next.plannedMin)
        assertEquals(2L, next.blockId)
    }

    @Test fun breakFinished_startsNextStudyWithAutoFocus() {
        val next = RoutineFlow.next(afterBreak = true, b = study2, minute = 7 * 60, autoFocus = true, strict = true, nowMs = 0)!!
        assertTrue(!next.isBreak && next.strict)
        assertEquals(45, next.plannedMin)
    }

    @Test fun breakFinished_noAutoFocus_stops() {
        assertNull(RoutineFlow.next(afterBreak = true, b = study2, minute = 7 * 60, autoFocus = false, strict = false, nowMs = 0))
    }

    @Test fun studyFinished_nextIsStudy_noBreakTimer() {
        assertNull(RoutineFlow.next(afterBreak = false, b = study2, minute = 7 * 60, autoFocus = true, strict = false, nowMs = 0))
    }
}
