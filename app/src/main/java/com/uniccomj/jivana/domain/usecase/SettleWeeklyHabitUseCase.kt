package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.HabitCycleCalendar
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.model.HabitWeekId
import com.uniccomj.jivana.domain.model.SettledHabitWeek
import com.uniccomj.jivana.domain.model.WeeklyHabitProgress
import com.uniccomj.jivana.domain.model.WeeklyHabitSettlement
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class SettleWeeklyHabitUseCase @Inject constructor(private val clock: Clock) {
    /** The schedule and completion dates must belong to the habit for this cycle. */
    operator fun invoke(
        cycle: HabitCycleCalendar,
        schedule: HabitSchedule.WeeklyTarget,
        completedDates: Collection<LocalDate>,
        throughDate: LocalDate = LocalDate.now(clock)
    ): WeeklyHabitSettlement {
        val completed = completedDates.toSet()
        val settledWeeks = mutableListOf<SettledHabitWeek>()
        var openWeek: WeeklyHabitProgress? = null
        val activeWeeks = cycle.activeDates.groupBy { it.with(MondayOfWeek) }

        for ((monday, activeDates) in activeWeeks) {
            if (activeDates.first() > throughDate) break
            val sunday = monday.plusDays((DaysPerWeek - 1).toLong())
            val periodEnd = minOf(sunday, cycle.endDate ?: sunday)
            val progress = WeeklyHabitProgress(
                id = HabitWeekId(cycle.cycleId, monday),
                periodEnd = periodEnd,
                activeDays = activeDates.size,
                completedDays = activeDates.count { it <= throughDate && it in completed },
                effectiveTarget = effectiveTarget(schedule.completionsPerWeek, activeDates.size)
            )

            val closeDate = periodEnd.plusDays(1)
            val settlementDate = if (periodEnd == cycle.endDate) {
                closeDate
            } else {
                cycle.firstUnpausedOnOrAfter(closeDate)
            }
            if (settlementDate != null && settlementDate <= throughDate) {
                settledWeeks += SettledHabitWeek(progress, settlementDate)
            } else {
                openWeek = progress
            }
        }

        return WeeklyHabitSettlement(settledWeeks.toList(), openWeek)
    }

    private fun effectiveTarget(target: Int, activeDays: Int): Int =
        (target * activeDays + DaysPerWeek - 1) / DaysPerWeek

    private companion object {
        val MondayOfWeek = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        val DaysPerWeek = DayOfWeek.entries.size
    }
}
