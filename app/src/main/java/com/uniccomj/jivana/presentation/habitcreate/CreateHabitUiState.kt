package com.uniccomj.jivana.presentation.habitcreate

import com.uniccomj.jivana.domain.usecase.HabitValidationError
import java.time.LocalDate

enum class HabitSaveFailure { ACTIVE_HABIT, REQUEST_TOKEN, STORAGE }

data class CreateHabitUiState(
    val configuring: Boolean = false,
    val name: String = "",
    val description: String = "",
    val weekly: Boolean = false,
    val weeklyTarget: Int = DefaultWeeklyTarget,
    val startDate: LocalDate? = null,
    val saving: Boolean = false,
    val validationError: HabitValidationError? = null,
    val saveFailure: HabitSaveFailure? = null,
    val saved: Boolean = false
) {
    internal val canEdit get() = configuring && !saving && !saved
}

internal const val DefaultWeeklyTarget = 3
