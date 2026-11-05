package com.uniccomj.jivana.domain.repository

import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitScheduleProgress
import java.time.LocalDate

interface HabitRepository {
    /** Atomically saves the definition and initial plan; the same id is an idempotent retry. */
    suspend fun create(habit: Habit, initialDates: Set<LocalDate>): Habit

    suspend fun getById(id: String): Habit?

    suspend fun getPendingSchedules(throughDate: LocalDate): List<HabitScheduleProgress>

    /** Commits dates and cursor together; false means another generator already advanced it. */
    suspend fun advanceSchedule(
        progress: HabitScheduleProgress,
        throughDate: LocalDate,
        dates: Set<LocalDate>
    ): Boolean
}
