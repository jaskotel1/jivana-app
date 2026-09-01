package com.uniccomj.jivana.domain.model

import java.time.LocalDate

data class DailyHabitPerformance(
    val date: LocalDate,
    val plannedHabitCount: Int,
    val completedHabitCount: Int,
    val activityRecorded: Boolean
) {
    init {
        require(plannedHabitCount >= 0) { "Planned habit count cannot be negative" }
        require(completedHabitCount in 0..plannedHabitCount) {
            "Completed habit count must be between zero and the planned habit count"
        }
        require(completedHabitCount == 0 || activityRecorded) {
            "Completing a habit requires recorded activity"
        }
    }

    val completionRate: Double?
        get() = if (plannedHabitCount ==
            0
        ) {
            null
        } else {
            completedHabitCount.toDouble() / plannedHabitCount
        }
}
