package com.uniccomj.jivana.domain.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HabitCycleTest {
    private val start = LocalDate.of(2027, 12, 29)
    private val cycle = HabitCycle("cycle", "habit", start)

    @Test
    fun `progress crosses the new year and finishes only after day sixty`() {
        assertProgress(cycle, start.minusDays(1), HabitCycleStatus.SCHEDULED, 0)
        assertProgress(cycle, start, HabitCycleStatus.ACTIVE, 1)
        assertProgress(cycle, start.plusDays(1), HabitCycleStatus.ACTIVE, 2)
        assertProgress(cycle, LocalDate.of(2028, 1, 1), HabitCycleStatus.ACTIVE, 4)
        assertProgress(cycle, start.plusDays(58), HabitCycleStatus.ACTIVE, 59)
        assertProgress(cycle, start.plusDays(59), HabitCycleStatus.ACTIVE, 60)
        assertProgress(cycle, start.plusDays(60), HabitCycleStatus.COMPLETED, 60)
        assertProgress(cycle, start.plusYears(1), HabitCycleStatus.COMPLETED, 60)
    }

    @Test
    fun `pauses freeze progress and shift the end across leap day`() {
        val pause = start.plusDays(25)
        val resume = pause.plusDays(7)
        val paused = cycle.copy(pauses = listOf(HabitCyclePause(pause)))
        val resumed = cycle.copy(pauses = listOf(HabitCyclePause(pause, resume)))

        assertProgress(paused, pause.plusDays(14), HabitCycleStatus.PAUSED, 25)
        assertNull(paused.calendar.endDate)
        assertProgress(resumed, pause, HabitCycleStatus.PAUSED, 25)
        assertProgress(resumed, resume.minusDays(1), HabitCycleStatus.PAUSED, 25)
        assertProgress(resumed, resume, HabitCycleStatus.ACTIVE, 26)
        assertEquals(LocalDate.of(2028, 3, 4), resumed.calendar.endDate)
    }

    @Test
    fun `local dates in either direction project the same active calendar`() {
        val instant = Instant.parse("2028-01-01T00:30:00Z")
        val utc = Clock.fixed(instant, ZoneOffset.UTC)
        val california = utc.withZone(ZoneId.of("America/Los_Angeles"))
        val original = cycle.progressOn(LocalDate.now(utc))

        assertProgress(cycle, LocalDate.now(california), HabitCycleStatus.ACTIVE, 3)
        assertProgress(cycle, LocalDate.now(utc), HabitCycleStatus.ACTIVE, 4)
        assertEquals(original, cycle.progressOn(LocalDate.now(utc)))
        assertEquals((0L..59L).map(start::plusDays), cycle.calendar.activeDates)
    }

    @Test
    fun `archive preserves the last eligible day and finished cycles`() {
        val archiveDate = start.plusDays(3)
        val archived = cycle.copy(archivedOn = archiveDate)
        assertProgress(archived, archiveDate.plusWeeks(1), HabitCycleStatus.ARCHIVED, 4)
        assertEquals(archiveDate, archived.calendar.activeDates.last())
        assertNull(archived.calendar.endDate)

        val completed = cycle.copy(archivedOn = start.plusDays(70))
        assertProgress(completed, start.plusDays(70), HabitCycleStatus.COMPLETED, 60)
        assertEquals(cycle.calendar.endDate, completed.calendar.endDate)
    }

    private fun assertProgress(
        cycle: HabitCycle,
        date: LocalDate,
        status: HabitCycleStatus,
        days: Int
    ) {
        val progress = cycle.progressOn(date)
        assertEquals(status, progress.status)
        assertEquals(days, progress.activeDays)
    }
}
