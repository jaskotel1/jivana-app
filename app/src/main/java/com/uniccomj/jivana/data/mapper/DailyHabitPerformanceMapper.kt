package com.uniccomj.jivana.data.mapper

import com.uniccomj.jivana.data.local.dao.DailyHabitPerformanceRow
import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import java.time.LocalDate

fun DailyHabitPerformanceRow.toDomain(): DailyHabitPerformance = DailyHabitPerformance(
    date = LocalDate.ofEpochDay(dateEpochDay),
    plannedHabitCount = plannedHabitCount,
    completedHabitCount = completedHabitCount,
    activityRecorded = activityRecorded
)
