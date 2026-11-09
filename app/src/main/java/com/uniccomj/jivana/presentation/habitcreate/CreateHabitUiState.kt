package com.uniccomj.jivana.presentation.habitcreate

import java.time.DayOfWeek

enum class HabitSaveFailure { ACTIVE_HABIT, REQUEST_TOKEN, STORAGE }

data class CreateHabitUiState(
    val name: String = "",
    val description: String = "",
    val scheduledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val everyDay: Boolean = true,
    val saving: Boolean = false,
    val nameRequired: Boolean = false,
    val daysRequired: Boolean = false,
    val saveFailure: HabitSaveFailure? = null,
    val saved: Boolean = false
)
