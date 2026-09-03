package com.uniccomj.jivana.data.repository

import com.uniccomj.jivana.data.local.dao.HabitHistoryDao
import com.uniccomj.jivana.data.local.entity.HabitScheduleEntity
import com.uniccomj.jivana.data.mapper.toDomain
import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHabitHistoryRepository @Inject constructor(
    private val dao: HabitHistoryDao,
    private val scoringConfig: JiveScoringConfig
) : HabitHistoryRepository {
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
        ).flatMapLatest { startEpochDay ->
            dao.observeDailyPerformance(
                fromEpochDay = startEpochDay ?: Long.MIN_VALUE,
                throughEpochDay = throughEpochDay
            )
        }.map { rows -> rows.map { row -> row.toDomain() } }
    }

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
