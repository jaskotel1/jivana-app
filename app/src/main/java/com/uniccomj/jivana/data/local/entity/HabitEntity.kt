package com.uniccomj.jivana.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

enum class HabitScheduleType { DAILY, WEEKLY_TARGET, LEGACY_WEEKDAYS }

internal const val AllWeekdaysMask = 127

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val scheduledDaysMask: Int,
    val startEpochDay: Long,
    val archivedAtEpochMillis: Long?,
    val scheduledThroughEpochDay: Long,
    @ColumnInfo(defaultValue = "'LEGACY_WEEKDAYS'") val scheduleType: HabitScheduleType,
    val weeklyTarget: Int?
) {
    init {
        require(
            when (scheduleType) {
                HabitScheduleType.DAILY ->
                    scheduledDaysMask == AllWeekdaysMask && weeklyTarget == null

                HabitScheduleType.WEEKLY_TARGET ->
                    scheduledDaysMask == 0 &&
                        weeklyTarget != null && weeklyTarget in 1..DayOfWeek.entries.size

                HabitScheduleType.LEGACY_WEEKDAYS ->
                    scheduledDaysMask in 1..AllWeekdaysMask && weeklyTarget == null
            }
        )
    }
}
