package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.HabitCycleCalendar
import com.uniccomj.jivana.domain.model.HabitCyclePause
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.model.HabitWeekId
import com.uniccomj.jivana.domain.model.HabitWeekOutcome.FAILURE
import com.uniccomj.jivana.domain.model.HabitWeekOutcome.SUCCESS
import com.uniccomj.jivana.domain.model.WeeklyHabitSettlement
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SettleWeeklyHabitUseCaseTest {
    private val monday = LocalDate.of(2026, 1, 5)
    private val cycle = HabitCycleCalendar("cycle", monday)
    private val threePerWeek = HabitSchedule.WeeklyTarget(3)
    private val settle = SettleWeeklyHabitUseCase(Clock.systemUTC())

    @ParameterizedTest
    @ValueSource(ints = [0, 2, 3, 4])
    fun `a full week produces exactly one outcome only after Sunday`(completions: Int) {
        val dates = cycle.activeDates.take(completions)

        val sunday = monday.plusDays(6)
        val open = settle(cycle, threePerWeek, dates, sunday)
        assertTrue(open.settledWeeks.isEmpty())
        val progress = requireNotNull(open.openWeek)
        assertEquals(sunday, progress.periodEnd)
        assertEquals(3, progress.effectiveTarget)
        assertEquals(completions, progress.completedDays)
        assertEquals(completions >= 3, progress.targetReached)

        val followingMonday = monday.plusWeeks(1)
        val result = settle(cycle, threePerWeek, dates, followingMonday).settledWeeks.single()
        assertEquals(progress, result.progress)
        assertEquals(followingMonday, result.settledOn)
        assertEquals(if (completions >= 3) SUCCESS else FAILURE, result.outcome)
    }

    @Test
    fun `duplicate future and pre-cycle completions do not inflate progress`() {
        val dates = listOf(monday.minusDays(1), monday, monday, monday.plusDays(4))
        val snapshot = settle(cycle, threePerWeek, dates, monday.plusDays(2))
        val progress = requireNotNull(snapshot.openWeek)

        assertEquals(1, progress.completedDays)
        assertFalse(progress.targetReached)
        assertTrue(snapshot.settledWeeks.isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "3, 7, 3",
        "3, 5, 3",
        "3, 4, 2",
        "3, 2, 1",
        "3, 1, 1",
        "5, 3, 3",
        "1, 7, 1",
        "1, 1, 1",
        "7, 7, 7",
        "7, 1, 1"
    )
    fun `effective target is rounded up from eligible days in the first ISO week`(
        target: Int,
        activeDays: Int,
        expected: Int
    ) {
        val start = monday.plusDays((7 - activeDays).toLong())
        val firstCycle = HabitCycleCalendar("cycle", start)
        val snapshot = settle(firstCycle, HabitSchedule.WeeklyTarget(target), listOf(start), start)
        val progress = requireNotNull(snapshot.openWeek)

        assertEquals(HabitWeekId("cycle", monday), progress.id)
        assertEquals(activeDays, progress.activeDays)
        assertEquals(expected, progress.effectiveTarget)
        assertEquals(1, progress.completedDays)
    }

    @Test
    fun `no progress or settlement is emitted before the cycle starts`() {
        val before = monday.minusDays(1)

        assertEquals(
            WeeklyHabitSettlement(emptyList(), null),
            settle(cycle, threePerWeek, listOf(before), before)
        )
    }

    @Test
    fun `last partial week closes after the sixtieth active day`() {
        val pause = HabitCyclePause(monday.plusDays(10), monday.plusDays(11))
        val pausedCycle = HabitCycleCalendar("cycle", monday, listOf(pause))
        val lastDay = monday.plusDays(60)
        val followingDay = lastDay.plusDays(1)
        val dates = listOf(lastDay, followingDay)

        val open = requireNotNull(settle(pausedCycle, threePerWeek, dates, lastDay).openWeek)
        assertEquals(lastDay, open.periodEnd)
        assertEquals(5, open.activeDays)
        assertEquals(3, open.effectiveTarget)

        val closed = settle(pausedCycle, threePerWeek, dates, followingDay)
        assertNull(closed.openWeek)
        assertEquals(open, closed.settledWeeks.last().progress)
        assertEquals(followingDay, closed.settledWeeks.last().settledOn)
        assertEquals(1, closed.settledWeeks.last().progress.completedDays)
        assertEquals(closed, settle(pausedCycle, threePerWeek, dates, followingDay.plusYears(1)))
    }

    @Test
    fun `a pause starting outside the cycle cannot defer the final settlement`() {
        val followingDay = monday.plusDays(60)
        val pause = HabitCyclePause(followingDay)
        val calendar = HabitCycleCalendar("cycle", monday, listOf(pause))

        val snapshot = settle(calendar, threePerWeek, emptyList(), followingDay)

        assertNull(snapshot.openWeek)
        assertEquals(followingDay, snapshot.settledWeeks.last().settledOn)
    }

    @Test
    fun `weeks wholly inside a pause have no outcomes and an open pause defers closure`() {
        val pauseStart = monday.plusDays(3)
        val resumeDate = monday.plusWeeks(2)
        val dates = listOf(monday, monday.plusDays(2), monday.plusDays(4), monday.plusWeeks(1))
        val paused = HabitCycleCalendar("cycle", monday, listOf(HabitCyclePause(pauseStart)))
        val duringPause = settle(paused, threePerWeek, dates, resumeDate.minusDays(1))
        val deferred = requireNotNull(duringPause.openWeek)

        assertTrue(duringPause.settledWeeks.isEmpty())
        assertEquals(3, deferred.activeDays)
        assertEquals(2, deferred.effectiveTarget)
        assertEquals(2, deferred.completedDays)

        val pause = HabitCyclePause(pauseStart, resumeDate)
        val resumed = HabitCycleCalendar("cycle", monday, listOf(pause, pause))
        assertEquals(duringPause, settle(resumed, threePerWeek, dates, resumeDate.minusDays(1)))
        val afterResume = settle(resumed, threePerWeek, dates, resumeDate)
        val result = afterResume.settledWeeks.single()
        assertEquals(deferred, result.progress)
        assertEquals(resumeDate, result.settledOn)
        assertEquals(SUCCESS, result.outcome)
        assertEquals(resumeDate, afterResume.openWeek?.id?.monday)
        assertEquals(afterResume, settle(resumed, threePerWeek, dates, resumeDate))
    }

    @Test
    fun `pause spanning two weeks across the year is applied to each ISO week`() {
        val decemberMonday = LocalDate.of(2026, 12, 28)
        val januaryMonday = decemberMonday.plusWeeks(1)
        val resumeDate = januaryMonday.plusDays(2)
        val pause = HabitCyclePause(decemberMonday.plusDays(4), resumeDate)
        val calendar = HabitCycleCalendar("year-end", decemberMonday, listOf(pause))
        val dates = listOf(decemberMonday, decemberMonday.plusDays(3), januaryMonday, resumeDate)

        val deferred = settle(calendar, threePerWeek, dates, januaryMonday)
        assertTrue(deferred.settledWeeks.isEmpty())
        assertEquals(4, deferred.openWeek?.activeDays)
        assertEquals(2, deferred.openWeek?.effectiveTarget)

        val closed = settle(calendar, threePerWeek, dates, januaryMonday.plusWeeks(1))
        val (december, january) = closed.settledWeeks
        assertEquals(HabitWeekId("year-end", decemberMonday), december.progress.id)
        assertEquals(resumeDate, december.settledOn)
        assertEquals(SUCCESS, december.outcome)
        assertEquals(HabitWeekId("year-end", januaryMonday), january.progress.id)
        assertEquals(5, january.progress.activeDays)
        assertEquals(3, january.progress.effectiveTarget)
        assertEquals(1, january.progress.completedDays)
        assertEquals(FAILURE, january.outcome)
    }

    @Test
    fun `a pause after an earlier settlement leaves that outcome visible`() {
        val pause = HabitCyclePause(monday.plusWeeks(1).plusDays(1))
        val calendar = HabitCycleCalendar("cycle", monday, listOf(pause))
        val snapshot = settle(calendar, threePerWeek, emptyList(), monday.plusWeeks(3))

        assertEquals(monday.plusWeeks(1), snapshot.settledWeeks.single().settledOn)
        assertEquals(monday.plusWeeks(1), snapshot.openWeek?.id?.monday)
    }

    @Test
    fun `archiving during an open pause closes the deferred week without adding days`() {
        val archive = monday.plusWeeks(1)
        val calendar = HabitCycleCalendar(
            "archived",
            monday,
            listOf(HabitCyclePause(monday.plusDays(3))),
            archivedOn = archive
        )
        val snapshot = settle(calendar, threePerWeek, listOf(monday, archive), archive.plusDays(1))
        val settled = snapshot.settledWeeks.single()
        assertEquals(archive.plusDays(1), settled.settledOn)
        assertEquals(3, settled.progress.activeDays)
        assertEquals(1, settled.progress.completedDays)
        assertNull(snapshot.openWeek)
    }

    @Test
    fun `replaying missed weeks produces an ordered series of distinct outcomes after restart`() {
        val fifthMonday = monday.plusWeeks(4)
        val dates = (0L..2L).map(fifthMonday::plusDays)
        val through = fifthMonday.plusWeeks(1)
        val restarted = SettleWeeklyHabitUseCase(Clock.systemUTC())

        assertEquals(
            listOf(FAILURE, FAILURE, FAILURE),
            settle(cycle, threePerWeek, dates, monday.plusWeeks(3)).settledWeeks.map { it.outcome }
        )
        assertEquals(
            List(4) { FAILURE },
            settle(cycle, threePerWeek, dates, fifthMonday).settledWeeks.map { it.outcome }
        )
        val snapshot = settle(cycle, threePerWeek, dates, through)
        assertEquals(List(4) { FAILURE } + SUCCESS, snapshot.settledWeeks.map { it.outcome })
        assertEquals(5, snapshot.settledWeeks.map { it.progress.id }.toSet().size)
        val restartedCycle = HabitCycleCalendar("cycle", monday)
        assertEquals(snapshot, restarted(restartedCycle, threePerWeek, dates, through))
    }

    @Test
    fun `correction changes the outcome under the same period identity`() {
        val dates = cycle.activeDates.take(3)
        val through = monday.plusWeeks(1)
        val original = settle(cycle, threePerWeek, dates, through).settledWeeks.single()
        val correctedDates = dates.dropLast(1)
        val corrected = settle(cycle, threePerWeek, correctedDates, through).settledWeeks.single()

        assertEquals(original.progress.id, corrected.progress.id)
        assertEquals(original.settledOn, corrected.settledOn)
        assertEquals(SUCCESS, original.outcome)
        assertEquals(FAILURE, corrected.outcome)
    }

    @Test
    fun `the current device zone determines Monday without moving completion dates`() {
        val instant = Instant.parse("2026-01-12T00:30:00Z")
        val mondayClock = Clock.fixed(instant, ZoneOffset.UTC)
        val sundayClock = mondayClock.withZone(ZoneId.of("America/New_York"))
        val dates = cycle.activeDates.take(3)

        val closed = SettleWeeklyHabitUseCase(mondayClock)(cycle, threePerWeek, dates)
        val open = SettleWeeklyHabitUseCase(sundayClock)(cycle, threePerWeek, dates)

        assertTrue(open.settledWeeks.isEmpty())
        assertEquals(open.openWeek, closed.settledWeeks.single().progress)
    }
}
