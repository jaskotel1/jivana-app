package com.uniccomj.jivana.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class Habit(
    val id: String,
    val name: String,
    val description: String?,
    val scheduledDays: Set<DayOfWeek>,
    val startDate: LocalDate,
    val archivedAt: Instant? = null
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(scheduledDays.isNotEmpty())
    }
}

/** Persistence progress is separate from the user's weekly rule. */
data class HabitScheduleProgress(val habit: Habit, val scheduledThrough: LocalDate)
