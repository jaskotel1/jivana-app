package com.uniccomj.jivana.domain.repository

import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitScheduleProgress
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    /** Atomically saves the definition and initial plan; the same id is an idempotent retry. */
    suspend fun create(habit: Habit, initialDates: Set<LocalDate>): Habit

    suspend fun getById(id: String): Habit?

    /** Includes every active habit so that callers can resolve pre-existing conflicts. */
    fun observeActiveHabits(): Flow<List<Habit>>

    /** A retry returns the original archived habit. */
    suspend fun archive(id: String, archivedAt: ZonedDateTime): Habit

    /** Archive the other habits only if the active set still matches the user's selection. */
    suspend fun resolveActiveHabitConflict(
        keepId: String,
        expectedActiveIds: Set<String>,
        archivedAt: ZonedDateTime
    )

    /** Active day-based schedules with dates still to materialize through the given date. */
    suspend fun getPendingSchedules(throughDate: LocalDate): List<HabitScheduleProgress>

    /** Commits dates and cursor together; false means another generator already advanced it. */
    suspend fun advanceSchedule(
        progress: HabitScheduleProgress,
        throughDate: LocalDate,
        dates: Set<LocalDate>
    ): Boolean
}
