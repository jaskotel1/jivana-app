package com.uniccomj.jivana.domain.model

import java.time.LocalDate

/** The start date is paused and the resume date is active again. A null resume is an open pause. */
data class HabitCyclePause(val startDate: LocalDate, val resumeDate: LocalDate? = null) {
    init {
        require(resumeDate == null || resumeDate >= startDate)
    }

    internal operator fun contains(date: LocalDate): Boolean =
        date >= startDate && (resumeDate == null || date < resumeDate)
}

/** Local dates for a single cycle, including projected days when its end can be determined. */
class HabitCycleCalendar(
    val cycleId: String,
    startDate: LocalDate,
    pauses: Collection<HabitCyclePause> = emptyList(),
    val archivedOn: LocalDate? = null
) {
    init {
        require(cycleId.isNotBlank())
    }

    private val pauses = pauses.toList()

    val activeDates: List<LocalDate> = generateSequence(firstUnpausedOnOrAfter(startDate)) { date ->
        if (date == LocalDate.MAX) null else firstUnpausedOnOrAfter(date.plusDays(1))
    }.takeWhile { archivedOn == null || it <= archivedOn }
        .take(HabitCycleLengthDays).toList()

    /** The last active day is known only when all 60 active dates can be determined. */
    val endDate: LocalDate? get() = activeDates.getOrNull(HabitCycleLengthDays - 1)

    /** The last date that can affect settlement, including an early archival. */
    val settlementEndDate: LocalDate? get() = endDate ?: archivedOn

    internal fun firstUnpausedOnOrAfter(date: LocalDate): LocalDate? {
        var candidate = date
        while (true) {
            val pause = pauses.firstOrNull { candidate in it } ?: return candidate
            candidate = pause.resumeDate ?: return null
        }
    }
}

const val HabitCycleLengthDays = 60
