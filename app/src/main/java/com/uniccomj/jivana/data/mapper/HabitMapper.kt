package com.uniccomj.jivana.data.mapper

import com.uniccomj.jivana.data.local.entity.AllWeekdaysMask
import com.uniccomj.jivana.data.local.entity.HabitEntity
import com.uniccomj.jivana.data.local.entity.HabitScheduleType
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    description = description,
    schedule = when (scheduleType) {
        HabitScheduleType.DAILY -> HabitSchedule.Daily

        HabitScheduleType.WEEKLY_TARGET -> HabitSchedule.WeeklyTarget(requireNotNull(weeklyTarget))

        HabitScheduleType.LEGACY_WEEKDAYS -> HabitSchedule.LegacyWeekdays(
            DayOfWeek.entries.filter { scheduledDaysMask and (1 shl it.ordinal) != 0 }.toSet()
        )
    },
    startDate = LocalDate.ofEpochDay(startEpochDay),
    archivedAt = archivedAtEpochMillis?.let(Instant::ofEpochMilli)
)

fun Habit.toEntity(scheduledThrough: LocalDate): HabitEntity = HabitEntity(
    id = id,
    name = name,
    description = description,
    scheduledDaysMask = when (val rule = schedule) {
        HabitSchedule.Daily -> AllWeekdaysMask

        is HabitSchedule.WeeklyTarget -> 0

        is HabitSchedule.LegacyWeekdays -> rule.days.fold(0) { mask, day ->
            mask or (1 shl day.ordinal)
        }
    },
    startEpochDay = startDate.toEpochDay(),
    archivedAtEpochMillis = archivedAt?.toEpochMilli(),
    scheduledThroughEpochDay = scheduledThrough.toEpochDay(),
    scheduleType = when (schedule) {
        HabitSchedule.Daily -> HabitScheduleType.DAILY
        is HabitSchedule.WeeklyTarget -> HabitScheduleType.WEEKLY_TARGET
        is HabitSchedule.LegacyWeekdays -> HabitScheduleType.LEGACY_WEEKDAYS
    },
    weeklyTarget = (schedule as? HabitSchedule.WeeklyTarget)?.completionsPerWeek
)
