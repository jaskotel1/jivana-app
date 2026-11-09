package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SetHabitCompletionUseCase @Inject constructor(
    private val habits: HabitRepository,
    private val history: HabitHistoryRepository,
    private val ensureSchedules: EnsureHabitSchedulesUseCase,
    private val clock: Clock
) {
    suspend operator fun invoke(habitId: String, date: LocalDate, completed: Boolean) {
        if (date <= LocalDate.now(clock)) {
            val habit = habits.getById(habitId)?.takeIf { it.archivedAt == null }
            if (habit != null && date >= habit.startDate &&
                habit.schedule.isPlannedOn(date.dayOfWeek)
            ) {
                ensureSchedules(date)
            }
        }
        // Room checks the current definition and planned day again within the write transaction.
        history.setHabitCompleted(habitId, date, completed)
    }
}
