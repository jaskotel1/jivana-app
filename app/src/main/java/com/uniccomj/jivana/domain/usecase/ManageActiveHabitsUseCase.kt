package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject

class ManageActiveHabitsUseCase @Inject constructor(
    private val repository: HabitRepository,
    private val ensureHabitSchedules: EnsureHabitSchedulesUseCase,
    private val clock: Clock
) {
    fun observeActiveHabits() = repository.observeActiveHabits()

    suspend fun archive(id: String) {
        val archivedAt = ZonedDateTime.now(clock)
        ensureHabitSchedules(archivedAt.toLocalDate())
        repository.archive(id, archivedAt)
    }

    suspend fun resolveConflict(keepId: String, expectedActiveIds: Set<String>) {
        val archivedAt = ZonedDateTime.now(clock)
        ensureHabitSchedules(archivedAt.toLocalDate())
        repository.resolveActiveHabitConflict(keepId, expectedActiveIds, archivedAt)
    }
}
