package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.HabitCycleProgress
import com.uniccomj.jivana.domain.repository.HabitCycleRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TrackHabitCycleUseCase @Inject constructor(
    private val cycles: HabitCycleRepository,
    private val clock: Clock
) {
    fun observeProgress(habitId: String, dates: Flow<LocalDate>): Flow<HabitCycleProgress?> =
        cycles.observeCycle(habitId).combine(dates) { cycle, date -> cycle?.progressOn(date) }

    suspend fun start(habitId: String) {
        cycles.startFirstCycle(habitId, LocalDate.now(clock))
    }
}
