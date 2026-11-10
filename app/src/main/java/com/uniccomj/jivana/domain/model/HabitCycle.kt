package com.uniccomj.jivana.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        val pause = pauses.lastOrNull { date in it }
        val status = when {
            completed -> HabitCycleStatus.COMPLETED
            archived -> HabitCycleStatus.ARCHIVED
            date < startDate -> HabitCycleStatus.SCHEDULED
            pause != null -> HabitCycleStatus.PAUSED
            else -> HabitCycleStatus.ACTIVE
        }
        return HabitCycleProgress(
            status = status,
            activeDays = calendar.activeDates.count { it <= date },
            startDate = startDate,
            endDate = endDate,
            pause = pause?.let {
                HabitPauseProgress(
                    it.startDate,
                    ChronoUnit.DAYS.between(it.startDate, date) + 1,
                    it.resumeDate == null
                )
            }
        )
    }
}

data class HabitCycleProgress(
    val status: HabitCycleStatus,
    val activeDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val pause: HabitPauseProgress? = null,
    val remainingPauses: Int = 0
)

data class HabitPauseProgress(val startDate: LocalDate, val days: Long, val canResume: Boolean)
