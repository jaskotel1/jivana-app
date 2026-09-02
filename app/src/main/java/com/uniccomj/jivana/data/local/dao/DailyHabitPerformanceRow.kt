package com.uniccomj.jivana.data.local.dao

data class DailyHabitPerformanceRow(
    val dateEpochDay: Long,
    val plannedHabitCount: Int,
    val completedHabitCount: Int,
    val activityRecorded: Boolean
)
