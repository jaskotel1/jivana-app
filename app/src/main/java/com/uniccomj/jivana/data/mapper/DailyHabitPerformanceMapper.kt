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

fun List<DailyHabitPerformanceRow>.toDomainHistory(
    throughDate: LocalDate
): List<DailyHabitPerformance> {
    if (isEmpty()) return emptyList()

    val rowsByDate = associateBy { row -> LocalDate.ofEpochDay(row.dateEpochDay) }
    val firstDate = requireNotNull(rowsByDate.keys.minOrNull())

    return buildList {
        var date = firstDate
        while (!date.isAfter(throughDate)) {
            add(
                rowsByDate[date]?.toDomain() ?: DailyHabitPerformance(
                    date = date,
                    plannedHabitCount = 0,
                    completedHabitCount = 0,
                    activityRecorded = false
                )
            )
            date = date.plusDays(1)
        }
    }
}
