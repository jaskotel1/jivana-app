package com.uniccomj.jivana.data.mapper

import com.uniccomj.jivana.data.local.entity.HabitEntity
import com.uniccomj.jivana.domain.model.Habit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    description = description,
    scheduledDays = DayOfWeek.entries.filter {
        scheduledDaysMask and (1 shl it.ordinal) != 0
    }.toSet(),
    startDate = LocalDate.ofEpochDay(startEpochDay),
    archivedAt = archivedAtEpochMillis?.let(Instant::ofEpochMilli)
)

fun Habit.toEntity(scheduledThrough: LocalDate): HabitEntity = HabitEntity(
    id = id,
    name = name,
    description = description,
    scheduledDaysMask = scheduledDays.fold(0) { mask, day -> mask or (1 shl day.ordinal) },
    startEpochDay = startDate.toEpochDay(),
    archivedAtEpochMillis = archivedAt?.toEpochMilli(),
    scheduledThroughEpochDay = scheduledThrough.toEpochDay()
)
