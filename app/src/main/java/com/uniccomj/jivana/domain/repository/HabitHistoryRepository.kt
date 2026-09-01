package com.uniccomj.jivana.domain.repository

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface HabitHistoryRepository {
    fun observeDailyPerformance(throughDate: LocalDate): Flow<List<DailyHabitPerformance>>

    suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>)

    suspend fun setHabitCompleted(habitId: String, date: LocalDate, completed: Boolean)

    suspend fun recordDailyCheckIn(date: LocalDate)
}
