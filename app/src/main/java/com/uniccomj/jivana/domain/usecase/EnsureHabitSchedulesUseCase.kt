package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.Clock
import java.time.LocalDate

class EnsureHabitSchedulesUseCase(
    private val repository: HabitRepository,
    private val clock: Clock
) {
    /** Returns whether unfinished progress was found, including work committed by another caller. */
    suspend operator fun invoke(throughDate: LocalDate = LocalDate.now(clock)): Boolean {
        require(throughDate <= LocalDate.now(clock)) { "Cannot generate future schedules" }
        var hadPendingSchedules = false
        do {
            val pending = repository.getPendingSchedules(throughDate)
            if (pending.isNotEmpty()) hadPendingSchedules = true
            for (progress in pending) {
                // Bounded transactions also allow cancellation during a long absence.
                val batchEnd = minOf(throughDate, progress.scheduledThrough.plusDays(BatchDays))
                val firstDay =
                    maxOf(progress.habit.startDate, progress.scheduledThrough.plusDays(1))
                val dates = generateSequence(firstDay) { it.plusDays(1) }
                    .takeWhile { it <= batchEnd }
                    .filter { it.dayOfWeek in progress.habit.scheduledDays }
                    .toSet()
                repository.advanceSchedule(progress, batchEnd, dates)
            }
        } while (pending.isNotEmpty())
        return hadPendingSchedules
    }

    private companion object {
        const val BatchDays = 366L
    }
}
