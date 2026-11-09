package com.uniccomj.jivana.data.repository

import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.local.entity.HabitScheduleEntity
import com.uniccomj.jivana.data.mapper.toDomain
import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.repository.HabitCompletionRejection
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHabitHistoryRepository @Inject constructor(
    private val database: JivanaDatabase,
    private val scoringConfig: JiveScoringConfig,
    private val completionEvents: HabitCompletionEvents,
    private val clock: Clock
) : HabitHistoryRepository {
    private val dao get() = database.habitHistoryDao()

    override fun observeDailyPerformance(
        throughDate: LocalDate
    ): Flow<List<DailyHabitPerformance>> {
        val throughEpochDay = throughDate.toEpochDay()
        return dao.observeJiveHistoryStartEpochDay(
            throughEpochDay = throughEpochDay,
            moodRollingWindowDays = scoringConfig.moodRollingWindowDays,
            weakEnergyThreshold = scoringConfig.weakEnergyThreshold,
            energyRecoveryThreshold = scoringConfig.energyRecoveryThreshold,
            exhaustedAfterWeakDays = scoringConfig.exhaustedAfterWeakDays,
            energyFullRecoveryDays = scoringConfig.energyFullRecoveryDays,
            sleepinessRecoveryThreshold = scoringConfig.sleepinessRecoveryThreshold,
            sleepingAfterInactiveDays = scoringConfig.sleepingAfterInactiveDays,
            sleepinessFullRecoveryDays = scoringConfig.sleepinessFullRecoveryDays
        ).combine(database.invalidationTracker.createFlow("habits")) { startEpochDay, _ ->
            // A new habit can commit without any planned day to invalidate the history tables.
            startEpochDay
        }.flatMapLatest { startEpochDay ->
            dao.observeDailyPerformance(
                fromEpochDay = startEpochDay ?: Long.MIN_VALUE,
                throughEpochDay = throughEpochDay
            )
        }.map { rows -> rows.map { row -> row.toDomain() } }
    }

    override fun observeCompletedDates(
        habitId: String,
        fromDate: LocalDate,
        throughDate: LocalDate
    ): Flow<List<LocalDate>> =
        dao.observeCompletedEpochDays(habitId, fromDate.toEpochDay(), throughDate.toEpochDay())
            .distinctUntilChanged()
            .map { dates -> dates.map(LocalDate::ofEpochDay) }

    override suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>) {
        require(habitId.isNotBlank()) { "Habit id cannot be blank" }
        dao.insertSchedules(
            dates.map { date -> HabitScheduleEntity(habitId, date.toEpochDay()) }
        )
    }

    override suspend fun setHabitCompleted(habitId: String, date: LocalDate, completed: Boolean) {
        require(habitId.isNotBlank()) { "Habit id cannot be blank" }
        if (date > LocalDate.now(clock)) {
            throw HabitOperationException.CompletionRejected(HabitCompletionRejection.FUTURE_DATE)
        }
        if (dao.setHabitCompleted(habitId, date.toEpochDay(), completed)) {
            // The transaction has committed; ignored duplicate inserts never celebrate.
            completionEvents.notifyCompletion()
        }
    }

    override suspend fun recordDailyCheckIn(date: LocalDate) {
        dao.recordDailyCheckIn(date.toEpochDay())
    }
}
