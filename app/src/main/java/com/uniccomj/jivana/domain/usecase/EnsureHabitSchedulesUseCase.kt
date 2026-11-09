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
        var hadPendingSchedules = false
        do {
            val pending = repository.getPendingSchedules(minOf(throughDate, LocalDate.now(clock)))
            if (pending.isNotEmpty()) hadPendingSchedules = true
            for (progress in pending) {
                // Bounded transactions also allow cancellation during a long absence.
                val batchEnd = minOf(
                    throughDate,
                    LocalDate.now(clock),
                    progress.scheduledThrough.plusDays(BatchDays)
                )
                if (batchEnd <= progress.scheduledThrough) continue
                val firstDay =
                    maxOf(progress.habit.startDate, progress.scheduledThrough.plusDays(1))
                val dates = generateSequence(firstDay) { it.plusDays(1) }
                    .takeWhile { it <= batchEnd }
                    .filter { progress.habit.schedule.isPlannedOn(it.dayOfWeek) }
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
