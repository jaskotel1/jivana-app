package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ObserveJiveConditionUseCase(
    private val habitHistoryRepository: HabitHistoryRepository,
    private val scorer: JiveConditionScorer,
    private val ensureHabitSchedules: EnsureHabitSchedulesUseCase
) {
    operator fun invoke(throughDate: LocalDate): Flow<JiveCondition> = flow {
        ensureHabitSchedules(throughDate)
        emitAll(
            habitHistoryRepository.observeDailyPerformance(throughDate)
                .map { snapshot ->
                    // A creation started before midnight may commit after the day refresh.
                    // If it introduced overdue progress, reload after catching up before scoring.
                    if (ensureHabitSchedules(throughDate)) {
                        habitHistoryRepository.observeDailyPerformance(throughDate).first()
                    } else {
                        snapshot
                    }
                }
                .map(scorer::score)
                .distinctUntilChanged()
        )
    }
}
