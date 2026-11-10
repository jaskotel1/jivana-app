package com.uniccomj.jivana.domain.repository

import com.uniccomj.jivana.domain.model.HabitCycle
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface HabitCycleRepository {
    /** The cycle remains readable by habit ID after completion or archival. */
    fun observeCycle(habitId: String): Flow<HabitCycle?>

    /** Explicit opt-in for an existing habit. A retry returns the original cycle. */
    suspend fun startFirstCycle(habitId: String, onDate: LocalDate): HabitCycle

    /** Starts a pause on the given current local date. Repeated starts are idempotent. */
    suspend fun pause(habitId: String, onDate: LocalDate)

    /** Closes only the interval the caller observed, keeping later intervals safe from retries. */
    suspend fun resume(habitId: String, pauseStart: LocalDate, onDate: LocalDate)
}
