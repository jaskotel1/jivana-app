package com.uniccomj.jivana.domain.repository

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface HabitHistoryRepository {
    fun observeDailyPerformance(throughDate: LocalDate): Flow<List<DailyHabitPerformance>>

    /** Emits completed dates for one habit in ascending order, including both bounds. */
    fun observeCompletedDates(
        habitId: String,
        fromDate: LocalDate,
        throughDate: LocalDate
    ): Flow<List<LocalDate>>

    suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>)

    /** Sets the desired state for a local date. Repeating the same state is safe. */
    suspend fun setHabitCompleted(habitId: String, date: LocalDate, completed: Boolean)

    suspend fun recordDailyCheckIn(date: LocalDate)
}
