package com.aravind.habitmaker.core.blocking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the blocker is doing right now, shown on the Block tab to help find problems. */
data class BlockerState(
    /** The accessibility service is running (not just switched on in Settings). */
    val running: Boolean = false,
    /** Last app seen in front. */
    val lastApp: String? = null,
    /** Last block: "app · reason". */
    val lastBlock: String? = null,
)

object BlockerStatus {
    private val _state = MutableStateFlow(BlockerState())
    val state: StateFlow<BlockerState> = _state.asStateFlow()

    fun running(on: Boolean) = _state.update { it.copy(running = on) }
    fun sawApp(pkg: String) { if (_state.value.lastApp != pkg) _state.update { it.copy(lastApp = pkg) } }
    fun blocked(pkg: String, reason: BlockReason) = _state.update { it.copy(lastBlock = "$pkg · ${reason.title}") }
}
