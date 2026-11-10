package com.uniccomj.jivana.domain.model

/** A pause consumes a slot when it starts, including a pause resumed on the same date. */
data class HabitPausePolicy(val maxStartsPerCycle: Int) {
    init {
        require(maxStartsPerCycle >= 0)
    }

    fun remainingStarts(cycle: HabitCycle): Int =
        (maxStartsPerCycle - cycle.pauses.size).coerceAtLeast(0)

    companion object {
        val Default = HabitPausePolicy(maxStartsPerCycle = 2)
    }
}
