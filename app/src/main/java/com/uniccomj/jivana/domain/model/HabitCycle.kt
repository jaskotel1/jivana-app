package com.uniccomj.jivana.domain.model

import java.time.LocalDate

enum class HabitCycleStatus { SCHEDULED, ACTIVE, PAUSED, COMPLETED, ARCHIVED }

/** Durable inputs to the calendar. The day number and lifecycle are projections for a local date. */
data class HabitCycle(
    val id: String,
    val habitId: String,
    val startDate: LocalDate,
    val archivedOn: LocalDate? = null,
    val pauses: List<HabitCyclePause> = emptyList()
) {
    val calendar = HabitCycleCalendar(id, startDate, pauses, archivedOn)

    fun progressOn(date: LocalDate): HabitCycleProgress {
        val endDate = calendar.endDate
        val archived = archivedOn != null && date >= archivedOn
        val completed = endDate != null && (date > endDate || (archived && archivedOn == endDate))
        val status = when {
            completed -> HabitCycleStatus.COMPLETED
            archived -> HabitCycleStatus.ARCHIVED
            date < startDate -> HabitCycleStatus.SCHEDULED
            pauses.any { date in it } -> HabitCycleStatus.PAUSED
            else -> HabitCycleStatus.ACTIVE
        }
        return HabitCycleProgress(
            status = status,
            activeDays = calendar.activeDates.count { it <= date },
            startDate = startDate,
            endDate = endDate
        )
    }
}

data class HabitCycleProgress(
    val status: HabitCycleStatus,
    val activeDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?
)
