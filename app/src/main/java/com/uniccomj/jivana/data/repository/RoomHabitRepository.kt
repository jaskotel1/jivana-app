package com.uniccomj.jivana.data.repository

import androidx.room.withTransaction
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.mapper.toDomain
import com.uniccomj.jivana.data.mapper.toEntity
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitScheduleProgress
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

class RoomHabitRepository @Inject constructor(
    private val database: JivanaDatabase,
    private val history: HabitHistoryRepository
) : HabitRepository {
    private val dao get() = database.habitDao()

    override suspend fun create(habit: Habit, initialDates: Set<LocalDate>): Habit =
        database.withTransaction {
            val existing = dao.getById(habit.id)?.toDomain()
            if (existing != null) {
                require(
                    existing.name == habit.name && existing.description == habit.description &&
                        existing.scheduledDays == habit.scheduledDays
                ) {
                    "Request token already used for another habit"
                }
                existing
            } else {
                val expectedDates = if (habit.startDate.dayOfWeek in habit.scheduledDays) {
                    setOf(habit.startDate)
                } else {
                    emptySet()
                }
                require(initialDates == expectedDates)
                dao.insert(habit.toEntity(scheduledThrough = habit.startDate))
                history.scheduleHabit(habit.id, initialDates)
                habit
            }
        }

    override suspend fun getById(id: String): Habit? = dao.getById(id)?.toDomain()

    override suspend fun getPendingSchedules(throughDate: LocalDate): List<HabitScheduleProgress> =
        dao.getPendingSchedules(throughDate.toEpochDay()).map {
            HabitScheduleProgress(it.toDomain(), LocalDate.ofEpochDay(it.scheduledThroughEpochDay))
        }

    override suspend fun advanceSchedule(
        progress: HabitScheduleProgress,
        throughDate: LocalDate,
        dates: Set<LocalDate>
    ): Boolean = database.withTransaction {
        val current = dao.getById(progress.habit.id)
        if (current == null ||
            current.scheduledThroughEpochDay != progress.scheduledThrough.toEpochDay() ||
            current.archivedAtEpochMillis != null
        ) {
            false
        } else {
            require(throughDate > progress.scheduledThrough)
            history.scheduleHabit(progress.habit.id, dates)
            dao.updateScheduleProgress(progress.habit.id, throughDate.toEpochDay())
            true
        }
    }
}
