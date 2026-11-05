package com.uniccomj.jivana.presentation.habitcreate

import java.time.DayOfWeek

data class CreateHabitUiState(
    val name: String = "",
    val description: String = "",
    val scheduledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val everyDay: Boolean = true,
    val saving: Boolean = false,
    val nameRequired: Boolean = false,
    val daysRequired: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false
)
