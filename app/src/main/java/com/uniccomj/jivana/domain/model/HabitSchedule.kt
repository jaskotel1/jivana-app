package com.uniccomj.jivana.domain.model

import java.time.DayOfWeek

sealed interface HabitSchedule {
    data object Daily : HabitSchedule

    data class WeeklyTarget(val completionsPerWeek: Int) : HabitSchedule {
        init {
            require(completionsPerWeek in AllowedTargets)
        }

        companion object {
            val AllowedTargets = 1..DayOfWeek.entries.size
        }
    }

    data class LegacyWeekdays(val days: Set<DayOfWeek>) : HabitSchedule {
        init {
            require(days.isNotEmpty())
        }
    }

    fun isPlannedOn(day: DayOfWeek): Boolean = when (this) {
        Daily -> true
        is WeeklyTarget -> false
        is LegacyWeekdays -> day in days
    }
}
