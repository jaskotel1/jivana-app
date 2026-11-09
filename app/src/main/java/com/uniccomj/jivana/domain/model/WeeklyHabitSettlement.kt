package com.uniccomj.jivana.domain.model

import java.time.LocalDate

data class HabitWeekId(val cycleId: String, val monday: LocalDate)

data class WeeklyHabitProgress(
    val id: HabitWeekId,
    val periodEnd: LocalDate,
    val activeDays: Int,
    val completedDays: Int,
    val effectiveTarget: Int
) {
    val targetReached: Boolean get() = completedDays >= effectiveTarget
}

enum class HabitWeekOutcome { SUCCESS, FAILURE }

data class SettledHabitWeek(val progress: WeeklyHabitProgress, val settledOn: LocalDate) {
    val outcome: HabitWeekOutcome
        get() = if (progress.targetReached) HabitWeekOutcome.SUCCESS else HabitWeekOutcome.FAILURE
}

data class WeeklyHabitSettlement(
    val settledWeeks: List<SettledHabitWeek>,
    val openWeek: WeeklyHabitProgress?
)
