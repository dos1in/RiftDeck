package com.riftdeck.core.input

/** Axis-based D-pads may emit nothing between press and release. */
internal class HeldDirectionRepeater(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val emit: (GameAction) -> Unit,
) {
    private var held: GameAction? = null
    private val repeat = object : Runnable {
        override fun run() {
            val action = held ?: return
            emit(action)
            if (held == action) schedule(this, 145L)
        }
    }

    fun update(action: GameAction?) {
        require(action == null || action.isDirectional)
        if (held == action) return
        cancel(repeat)
        held = action
        if (action != null) {
            emit(action)
            if (held == action) schedule(repeat, 350L)
        }
    }
}
