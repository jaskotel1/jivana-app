package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveJiveConditionUseCase(
    private val habitHistoryRepository: HabitHistoryRepository,
    private val scorer: JiveConditionScorer
) {
    operator fun invoke(throughDate: LocalDate): Flow<JiveCondition> =
        habitHistoryRepository.observeDailyPerformance(throughDate)
            .map(scorer::score)
            .distinctUntilChanged()
}
