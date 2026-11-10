package com.uniccomj.jivana.data.mapper

import com.uniccomj.jivana.data.local.dao.HabitCycleWithPauses
import com.uniccomj.jivana.domain.model.HabitCycle
import com.uniccomj.jivana.domain.model.HabitCyclePause
import java.time.LocalDate

fun HabitCycleWithPauses.toDomain() = HabitCycle(
    id = cycle.id,
    habitId = cycle.habitId,
    startDate = LocalDate.ofEpochDay(cycle.startEpochDay),
    archivedOn = cycle.archivedEpochDay?.let(LocalDate::ofEpochDay),
    pauses = pauses.sortedBy { it.startEpochDay }.map {
        HabitCyclePause(
            LocalDate.ofEpochDay(it.startEpochDay),
            it.resumeEpochDay?.let(LocalDate::ofEpochDay)
        )
    }
)
