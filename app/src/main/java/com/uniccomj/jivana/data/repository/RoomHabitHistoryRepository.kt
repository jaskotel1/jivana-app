package com.uniccomj.jivana.data.repository

import com.uniccomj.jivana.data.local.dao.HabitHistoryDao
import com.uniccomj.jivana.data.local.entity.HabitScheduleEntity
import com.uniccomj.jivana.data.mapper.toDomainHistory
import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHabitHistoryRepository @Inject constructor(private val dao: HabitHistoryDao) :
    HabitHistoryRepository {
    override fun observeDailyPerformance(
        throughDate: LocalDate
    ): Flow<List<DailyHabitPerformance>> = dao.observeDailyPerformance(throughDate.toEpochDay())
        .map { rows -> rows.toDomainHistory(throughDate) }

    override suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>) {
        require(habitId.isNotBlank()) { "Habit id cannot be blank" }
        dao.insertSchedules(
            dates.map { date -> HabitScheduleEntity(habitId, date.toEpochDay()) }
        )
    }

    override suspend fun setHabitCompleted(habitId: String, date: LocalDate, completed: Boolean) {
        require(habitId.isNotBlank()) { "Habit id cannot be blank" }
        dao.setHabitCompleted(habitId, date.toEpochDay(), completed)
    }

    override suspend fun recordDailyCheckIn(date: LocalDate) {
        dao.recordDailyCheckIn(date.toEpochDay())
    }
}
