package com.uniccomj.jivana.domain.model

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HabitCycleCalendarTest {
    private val start = LocalDate.of(2026, 1, 5)

    @Test
    fun `cycle includes the start and ends after sixty active dates`() {
        val cycle = HabitCycleCalendar("cycle", start)

        assertEquals((0L..59L).map(start::plusDays), cycle.activeDates)
        assertEquals(start.plusDays(59), cycle.endDate)
    }

    @Test
    fun `seven paused days preserve day twenty five and shift the end by seven days`() {
        val pauseStarts = start.plusDays(25)
        val resumes = pauseStarts.plusDays(7)
        val openPause = HabitCycleCalendar(
            "cycle",
            start,
            listOf(HabitCyclePause(pauseStarts))
        )
        val resumed = HabitCycleCalendar(
            "cycle",
            start,
            listOf(HabitCyclePause(pauseStarts, resumes))
        )

        assertEquals(25, openPause.activeDates.size)
        assertNull(openPause.endDate)
        assertEquals(openPause.activeDates, resumed.activeDates.take(25))
        assertEquals(resumes, resumed.activeDates[25])
        assertEquals(60, resumed.activeDates.size)
        assertEquals(start.plusDays(66), resumed.endDate)
    }

    @Test
    fun `overlapping adjacent and repeated pauses count each skipped day once`() {
        val first = HabitCyclePause(start, start.plusDays(4))
        val overlap = HabitCyclePause(start.plusDays(1), start.plusDays(5))
        val adjacent = HabitCyclePause(start.plusDays(5), start.plusDays(6))
        val sameDay = HabitCyclePause(start.plusDays(7), start.plusDays(7))
        val pauses = listOf(adjacent, first, overlap, first, sameDay)
        val cycle = HabitCycleCalendar("cycle", start, pauses)

        assertEquals(start.plusDays(6), cycle.activeDates.first())
        assertEquals(start.plusDays(7), cycle.activeDates[1])
        assertEquals(start.plusDays(65), cycle.endDate)
    }

    @Test
    fun `a pause during the final day holds the cycle at fifty nine days`() {
        val daySixty = start.plusDays(59)
        val open = HabitCycleCalendar("cycle", start, listOf(HabitCyclePause(daySixty)))
        val resumed = HabitCycleCalendar(
            "cycle",
            start,
            listOf(HabitCyclePause(daySixty, daySixty.plusDays(2)))
        )

        assertEquals(59, open.activeDates.size)
        assertNull(open.endDate)
        assertEquals(daySixty.plusDays(2), resumed.endDate)
    }

    @Test
    fun `a resume date before pause start is invalid`() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitCyclePause(start, start.minusDays(1))
        }
    }
}
