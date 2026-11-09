package com.uniccomj.jivana.domain.model

import java.time.Instant
import java.time.LocalDate

data class Habit(
    val id: String,
    val name: String,
    val description: String?,
    val schedule: HabitSchedule,
    val startDate: LocalDate,
    val archivedAt: Instant? = null
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
    }
}

/** Cursor for materializing the dates of a day-based schedule. */
data class HabitScheduleProgress(val habit: Habit, val scheduledThrough: LocalDate)
