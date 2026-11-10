package com.uniccomj.jivana.data.repository

import androidx.room.withTransaction
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.local.entity.HabitScheduleType
import com.uniccomj.jivana.data.mapper.toDomain
import com.uniccomj.jivana.data.mapper.toEntity
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitScheduleProgress
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomHabitRepository @Inject constructor(
    private val database: JivanaDatabase,
    private val history: HabitHistoryRepository
) : HabitRepository {
    private val dao get() = database.habitDao()
    private val cycles get() = database.habitCycleDao()

    override suspend fun create(habit: Habit, initialDates: Set<LocalDate>): Habit =
        database.withTransaction {
            val existing = dao.getById(habit.id)?.toDomain()
            if (existing != null) {
                if (existing.name != habit.name || existing.description != habit.description ||
                    existing.schedule != habit.schedule
                ) {
                    throw HabitOperationException.RequestTokenConflict()
                }
                existing
            } else {
                if (habit.archivedAt == null && dao.countActive() >= MaxActiveHabits) {
                    throw HabitOperationException.ActiveHabitLimitReached()
                }
                val expectedDates = if (habit.schedule.isPlannedOn(habit.startDate.dayOfWeek)) {
                    setOf(habit.startDate)
                } else {
                    emptySet()
                }
                require(initialDates == expectedDates)
                dao.insert(habit.toEntity(scheduledThrough = habit.startDate))
                if (habit.archivedAt == null) cycles.insertCycle(habit.id, habit.startDate)
                if (initialDates.isNotEmpty()) history.scheduleHabit(habit.id, initialDates)
                habit
            }
        }

    override suspend fun getById(id: String): Habit? = dao.getById(id)?.toDomain()

    override fun observeActiveHabits(): Flow<List<Habit>> =
        dao.observeActiveHabits().map { habits ->
            habits.map { it.toDomain() }
        }.distinctUntilChanged()

    override suspend fun archive(id: String, archivedAt: ZonedDateTime): Habit =
        database.withTransaction {
            val current = dao.getById(id) ?: throw HabitOperationException.ActiveHabitsChanged()
            if (current.archivedAtEpochMillis != null) return@withTransaction current.toDomain()

            archiveActive(id, archivedAt)
            checkNotNull(dao.getById(id)).toDomain()
        }

    override suspend fun resolveActiveHabitConflict(
        keepId: String,
        expectedActiveIds: Set<String>,
        archivedAt: ZonedDateTime
    ) = database.withTransaction {
        val activeIds = dao.getActiveIds().toSet()
        if (activeIds.size <= MaxActiveHabits || activeIds != expectedActiveIds ||
            keepId !in activeIds
        ) {
            throw HabitOperationException.ActiveHabitsChanged()
        }
        (activeIds - keepId).forEach { archiveActive(it, archivedAt) }
    }

    private suspend fun archiveActive(id: String, archivedAt: ZonedDateTime) {
        val archiveEpochDay = archivedAt.toLocalDate().toEpochDay()
        dao.archive(id, archivedAt.toInstant().toEpochMilli())
        cycles.archive(id, archiveEpochDay)
        database.habitHistoryDao().deleteUncompletedSchedulesAfter(id, archiveEpochDay)
    }

    override suspend fun getPendingSchedules(throughDate: LocalDate): List<HabitScheduleProgress> =
        dao.getPendingSchedules(throughDate.toEpochDay()).map {
            HabitScheduleProgress(it.toDomain(), LocalDate.ofEpochDay(it.scheduledThroughEpochDay))
        }

    override suspend fun advanceSchedule(
        progress: HabitScheduleProgress,
        throughDate: LocalDate,
        dates: Set<LocalDate>
    ): Boolean = database.withTransaction {
        val current = dao.getById(progress.habit.id) ?: return@withTransaction false
        if (current.scheduledThroughEpochDay != progress.scheduledThrough.toEpochDay() ||
            current.archivedAtEpochMillis != null ||
            current.scheduleType == HabitScheduleType.WEEKLY_TARGET
        ) {
            false
        } else {
            require(throughDate > progress.scheduledThrough)
            history.scheduleHabit(progress.habit.id, dates)
            dao.updateScheduleProgress(progress.habit.id, throughDate.toEpochDay())
            true
        }
    }

    private companion object {
        const val MaxActiveHabits = 1
    }
}
