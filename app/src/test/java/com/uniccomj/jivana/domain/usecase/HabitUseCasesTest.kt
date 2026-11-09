package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.model.HabitScheduleProgress
import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HabitUseCasesTest {
    private val repository = MemoryHabits()
    private val clock = TestClock(Instant.parse("2026-11-05T12:00:00Z"))
    private val create = CreateHabitUseCase(repository, clock)
    private val ensure = EnsureHabitSchedulesUseCase(repository, clock)

    @Test
    fun `daily habit is normalized starts locally today and has a name-independent id`() = runTest {
        val first =
            create("  Drink   water  ", " \n Notes\nSecond line \n", DayOfWeek.entries.toSet())
        val second = create("Drink water", "", DayOfWeek.entries.toSet())
        assertEquals("Drink water", first.name)
        assertEquals("Notes\nSecond line", first.description)
        assertNull(second.description)
        assertFalse(first.id == second.id)
        assertEquals(LocalDate.of(2026, 11, 5), first.startDate)
        assertEquals(setOf(first.startDate), repository.dates.getValue(first.id))
        assertEquals(HabitSchedule.Daily, first.schedule)
        assertNull(first.archivedAt)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7])
    fun `weekly goals have no day based schedule`(goal: Int) = runTest {
        val schedule = HabitSchedule.WeeklyTarget(goal)
        val habit = create.createWithSchedule("Exercise", "", schedule)
        assertEquals(schedule, repository.getById(habit.id)?.schedule)
        clock.current = Instant.parse("2026-11-16T12:00:00Z")
        assertFalse(ensure())
        assertTrue(repository.dates.getValue(habit.id).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 8])
    fun `weekly goals outside the allowed range are rejected`(goal: Int) {
        assertThrows(IllegalArgumentException::class.java) { HabitSchedule.WeeklyTarget(goal) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\n\t"])
    fun `blank names are rejected without writes`(name: String) = runTest {
        try {
            create.createWithSchedule(name, "", HabitSchedule.Daily)
            error("Expected validation")
        } catch (error: HabitValidationException) {
            assertEquals(HabitValidationError.NAME_REQUIRED, error.error)
        }
        assertTrue(repository.habits.isEmpty())
    }

    @Test
    fun `at least one selected day is required`() = runTest {
        try {
            create("Water", "", emptySet())
            error("Expected validation")
        } catch (error: HabitValidationException) {
            assertEquals(HabitValidationError.DAYS_REQUIRED, error.error)
        }
        assertTrue(repository.habits.isEmpty())
    }

    @Test
    fun `typed creation validates the normalized name length`() = runTest {
        val maximum = "a".repeat(CreateHabitUseCase.MaxNameLength)
        try {
            create.createWithSchedule(maximum + "a", "", HabitSchedule.Daily)
            error("Expected validation")
        } catch (error: HabitValidationException) {
            assertEquals(HabitValidationError.NAME_TOO_LONG, error.error)
        }
        assertTrue(repository.habits.isEmpty())
        val accepted = create.createWithSchedule("  $maximum  ", "", HabitSchedule.Daily)
        assertEquals(maximum, accepted.name)
    }

    @Test
    fun `explicit future start is preserved and catch up starts there`() = runTest {
        val start = LocalDate.of(2026, 11, 7)
        val habit = create.createWithSchedule(
            "Water",
            "",
            HabitSchedule.Daily,
            "future-start",
            start
        )
        assertEquals(start, habit.startDate)
        assertEquals(setOf(start), repository.dates.getValue(habit.id))
        assertFalse(ensure())

        clock.currentZone = ZoneId.of("Asia/Tokyo")
        clock.current = Instant.parse("2026-11-07T16:00:00Z")
        val retry = create.createWithSchedule(
            "Water",
            "",
            HabitSchedule.Daily,
            "future-start",
            start
        )
        assertEquals(habit, retry)
        assertTrue(ensure())
        assertFalse(ensure())
        assertEquals(setOf(start, start.plusDays(1)), repository.dates.getValue(habit.id))
    }

    @Test
    fun `default start follows the current local date when a draft is saved`() = runTest {
        clock.current = Instant.parse("2026-11-05T23:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 5), create.today())
        clock.currentZone = ZoneId.of("Europe/Warsaw")
        val habit = create.createWithSchedule("Water", "", HabitSchedule.Daily)
        assertEquals(LocalDate.of(2026, 11, 6), habit.startDate)
        assertEquals(setOf(habit.startDate), repository.dates.getValue(habit.id))
    }

    @Test
    fun `same request token retries return original habit even after midnight`() = runTest {
        val first = create("Water", "", DayOfWeek.entries.toSet(), "same-request")
        clock.current = clock.current.plusSeconds(86_400)
        val retried = create("Water", "", DayOfWeek.entries.toSet(), "same-request")
        assertEquals(first, retried)
        assertEquals(1, repository.habits.size)
    }

    @Test
    fun `selected weekdays catch up after absence without future or pre-start records`() = runTest {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val habit = create("Exercise", "", days)
        assertTrue(repository.dates.getValue(habit.id).isEmpty()) // Starts on Thursday.
        clock.current = Instant.parse("2026-11-16T12:00:00Z")
        ensure()
        val expected = setOf(6, 9, 11, 13, 16).map { LocalDate.of(2026, 11, it) }.toSet()
        assertEquals(expected, repository.dates.getValue(habit.id))
        val writes = repository.advances
        repeat(3) { ensure() }
        assertEquals(writes, repository.advances)
        assertEquals(expected, repository.dates.getValue(habit.id))
    }

    @ParameterizedTest
    @ValueSource(strings = ["2026-01-30", "2026-12-30", "2028-02-27"])
    fun `daily planning crosses month year and leap day boundaries`(start: String) = runTest {
        val first = LocalDate.parse(start)
        clock.current = first.atTime(12, 0).toInstant(ZoneOffset.UTC)
        val habit = create("Daily", "", DayOfWeek.entries.toSet())
        clock.current = first.plusDays(5).atTime(12, 0).toInstant(ZoneOffset.UTC)
        ensure()
        assertEquals((0L..5L).map(first::plusDays).toSet(), repository.dates.getValue(habit.id))
    }

    @Test
    fun `long absence uses bounded cursor batches instead of rewriting earlier days`() = runTest {
        val habit = create("Daily", "", DayOfWeek.entries.toSet())
        clock.current = clock.current.plusSeconds(800L * 86_400)
        ensure()
        assertEquals(801, repository.dates.getValue(habit.id).size)
        assertEquals(3, repository.advances)
    }

    @Test
    fun `moving local date backwards does not rewind cursor and next local day catches up`() =
        runTest {
            clock.current = Instant.parse("2026-11-05T00:30:00Z")
            clock.currentZone = ZoneId.of("Asia/Tokyo")
            val habit = create("Daily", "", DayOfWeek.entries.toSet())
            clock.currentZone = ZoneId.of("America/Los_Angeles")
            ensure()
            assertEquals(setOf(LocalDate.of(2026, 11, 5)), repository.dates.getValue(habit.id))
            clock.current = Instant.parse("2026-11-06T09:00:00Z")
            ensure()
            assertEquals(
                setOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)),
                repository.dates.getValue(habit.id)
            )
        }

    private class TestClock(var current: Instant, var currentZone: ZoneId = ZoneOffset.UTC) :
        Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = currentZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }

    private class MemoryHabits : HabitRepository {
        val habits = mutableMapOf<String, Habit>()
        val dates = mutableMapOf<String, MutableSet<LocalDate>>()
        private val cursors = mutableMapOf<String, LocalDate>()
        var advances = 0

        override suspend fun create(habit: Habit, initialDates: Set<LocalDate>): Habit {
            habits[habit.id]?.let { return it }
            habits[habit.id] = habit
            dates[habit.id] = initialDates.toMutableSet()
            cursors[habit.id] = habit.startDate
            return habit
        }

        override suspend fun getById(id: String): Habit? = habits[id]

        override fun observeActiveHabits(): Flow<List<Habit>> = error("Not exercised here")

        override suspend fun archive(id: String, archivedAt: ZonedDateTime): Habit =
            error("Not exercised here")

        override suspend fun resolveActiveHabitConflict(
            keepId: String,
            expectedActiveIds: Set<String>,
            archivedAt: ZonedDateTime
        ): Unit = error("Not exercised here")

        override suspend fun getPendingSchedules(
            throughDate: LocalDate
        ): List<HabitScheduleProgress> = habits.values.filter {
            it.schedule !is HabitSchedule.WeeklyTarget && cursors.getValue(it.id) < throughDate
        }.map { HabitScheduleProgress(it, cursors.getValue(it.id)) }

        override suspend fun advanceSchedule(
            progress: HabitScheduleProgress,
            throughDate: LocalDate,
            dates: Set<LocalDate>
        ): Boolean {
            this.dates.getValue(progress.habit.id).addAll(dates)
            cursors[progress.habit.id] = throughDate
            advances++
            return true
        }
    }
}
