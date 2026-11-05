package com.uniccomj.jivana.domain.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Live notifications only: no replay, persistence, or backlog while Home is absent. */
class HabitCompletionEvents {
    private val mutableCompletions = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val completions = mutableCompletions.asSharedFlow()

    fun notifyCompletion() {
        mutableCompletions.tryEmit(Unit)
    }
}
