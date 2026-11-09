package com.uniccomj.jivana.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import com.uniccomj.jivana.domain.usecase.JiveConditionScorer
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import com.uniccomj.jivana.domain.usecase.ObserveJiveConditionUseCase
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHabitRepositoryTest {
    private lateinit var database: JivanaDatabase
    private lateinit var history: RoomHabitHistoryRepository
    private lateinit var repository: RoomHabitRepository
    private val events = HabitCompletionEvents()
    private val start = LocalDate.of(2026, 11, 5)
    private val startClock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                JivanaDatabase::class.java
            ).build()
        history = RoomHabitHistoryRepository(database, JiveScoringConfig(), events)
        repository = RoomHabitRepository(database, history)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createAndCatchUpPreserveCompletionWithoutEventsOrInteractions() = runTest {
        var reactions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.completions.collect { reactions++ }
        }
        val habit =
            CreateHabitUseCase(repository, startClock)("Water", "Notes", DayOfWeek.entries.toSet())
        assertEquals(habit, repository.getById(habit.id))
        assertEquals(0, reactions)
        assertEquals(
            false,
            history.observeDailyPerformance(start).first().single().activityRecorded
        )
        history.setHabitCompleted(habit.id, start, true)
        assertEquals(1, reactions)
        val end = start.plusDays(8)
        val endClock = Clock.fixed(end.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val ensure = EnsureHabitSchedulesUseCase(repository, endClock)
        // Competing generators must commit both the plan and cursor consistently.
        List(3) { async { ensure() } }.awaitAll()
        val performance = history.observeDailyPerformance(end).first()
        assertEquals(9, performance.size)
        assertEquals(1, performance.first().completedHabitCount)
        assertTrue(performance.drop(1).all { !it.activityRecorded && it.completedHabitCount == 0 })
        assertEquals(1, reactions)
        assertTrue(repository.getPendingSchedules(end).isEmpty())
        assertEquals(
            end.toEpochDay(),
            database.habitDao().getById(habit.id)!!.scheduledThroughEpochDay
        )
    }

    @Test
    fun concurrentRetriesCreateOnlyOneDefinitionAndInitialSchedule() = runTest {
        val create = CreateHabitUseCase(repository, startClock)
        val results = List(3) {
            async { create("Water", "", DayOfWeek.entries.toSet(), "same-token") }
        }.awaitAll()
        assertEquals(1, results.map { it.id }.toSet().size)
        assertEquals(1, history.observeDailyPerformance(start).first().single().plannedHabitCount)
    }

    @Test
    fun dailyAndWeeklyHabitsRoundTripWithoutAddingWeeklyObligations() = runTest {
        val create = CreateHabitUseCase(repository, startClock)
        val daily = create.createWithSchedule("Water", "", HabitSchedule.Daily)
        assertEquals(daily, repository.getById(daily.id))
        val baseline = history.observeDailyPerformance(start).first()
        val end = start.plusWeeks(2)
        val archiveTime = end.atTime(12, 0).atZone(ZoneOffset.UTC)
        val ensure = EnsureHabitSchedulesUseCase(
            repository,
            Clock.fixed(archiveTime.toInstant(), archiveTime.zone)
        )
        assertEquals(listOf(daily), repository.getPendingSchedules(end).map { it.habit })
        ensure()
        repository.archive(daily.id, archiveTime)
        val weeklyHabits = (1..7).map { goal ->
            val schedule = HabitSchedule.WeeklyTarget(goal)
            val habit = create.createWithSchedule("Exercise", "", schedule, "weekly-$goal")
            assertEquals(habit, repository.getById(habit.id))
            assertEquals(schedule, repository.getById(habit.id)?.schedule)
            assertEquals(habit, create.createWithSchedule("Exercise", "", schedule, "weekly-$goal"))
            assertTrue(repository.getPendingSchedules(end).isEmpty())
            assertEquals(false, ensure())
            repository.archive(habit.id, archiveTime)
            habit
        }
        assertEquals(baseline, history.observeDailyPerformance(start).first())

        val performance = database.habitHistoryDao()
            .observeDailyPerformance(start.toEpochDay(), end.toEpochDay()).first()
        assertEquals(15, performance.size)
        assertTrue(performance.all { it.plannedHabitCount == 1 })
        for (weekly in weeklyHabits) {
            val entity = database.habitDao().getById(weekly.id)!!
            assertEquals(start.toEpochDay(), entity.scheduledThroughEpochDay)
        }
    }

    @Test
    fun explicitStartAndInitialPlanCommitOnlyOnceAndStayOutsideEarlierHistory() = runTest {
        val create = CreateHabitUseCase(repository, startClock)
        val future = start.plusDays(3)
        val results = List(3) {
            async {
                create.createWithSchedule("Water", "", HabitSchedule.Daily, "same-token", future)
            }
        }.awaitAll()
        val habit = results.toSet().single()
        assertEquals(future, repository.getById(habit.id)?.startDate)
        assertTrue(history.observeDailyPerformance(start).first().isEmpty())
        assertEquals(1, history.observeDailyPerformance(future).first().single().plannedHabitCount)
        assertEquals(null, database.habitDao().getById(habit.id)?.weeklyTarget)

        val changedDate = runCatching {
            create.createWithSchedule(
                "Water",
                "",
                HabitSchedule.Daily,
                "same-token",
                future.plusDays(1)
            )
        }
        assertTrue(changedDate.exceptionOrNull() is HabitOperationException.RequestTokenConflict)
        assertEquals(habit, repository.getById(habit.id))
    }

    @Test
    fun invalidScheduleCombinationsCannotBeStored() = runTest {
        val create = CreateHabitUseCase(repository, startClock)
        val daily = create.createWithSchedule("Water", "", HabitSchedule.Daily)
        repository.archive(daily.id, start.atStartOfDay(ZoneOffset.UTC))
        val weekly = create.createWithSchedule("Exercise", "", HabitSchedule.WeeklyTarget(3))
        repository.archive(weekly.id, start.atStartOfDay(ZoneOffset.UTC))
        val dailyEntity = database.habitDao().getById(daily.id)!!
        val weeklyEntity = database.habitDao().getById(weekly.id)!!
        val invalidDaily = runCatching {
            database.habitDao().insert(dailyEntity.copy(id = "invalid-daily", weeklyTarget = 3))
        }
        val invalidWeekly = runCatching {
            database.habitDao().insert(
                weeklyEntity.copy(id = "invalid-weekly", scheduledDaysMask = 1)
            )
        }
        val invalidPlan = runCatching {
            repository.create(weekly.copy(id = "invalid-plan"), setOf(start))
        }
        assertTrue(invalidDaily.exceptionOrNull() is IllegalArgumentException)
        assertTrue(invalidWeekly.exceptionOrNull() is IllegalArgumentException)
        assertEquals(IllegalArgumentException::class.java, invalidPlan.exceptionOrNull()?.javaClass)
        assertTrue(repository.getById("invalid-daily") == null)
        assertTrue(repository.getById("invalid-weekly") == null)
        assertTrue(repository.getById("invalid-plan") == null)
    }

    @Test
    fun failedInitialScheduleRollsBackDefinitionAndAllInsertedDates() = runTest {
        val failingHistory = object : HabitHistoryRepository by history {
            override suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>) {
                history.scheduleHabit(habitId, dates)
                throw IOException("Simulated write failure after inserting schedules")
            }
        }
        val failing = RoomHabitRepository(database, failingHistory)
        try {
            CreateHabitUseCase(failing, startClock)("Water", "", DayOfWeek.entries.toSet())
            error("Expected failure")
        } catch (_: IOException) {
            assertTrue(history.observeDailyPerformance(start).first().isEmpty())
            assertTrue(repository.getPendingSchedules(start.plusDays(1)).isEmpty())
        }
    }

    @Test
    fun failedCatchUpDoesNotAdvanceCursorOrLeavePartiallyWrittenDates() = runTest {
        val habit =
            CreateHabitUseCase(repository, startClock)("Water", "", DayOfWeek.entries.toSet())
        val failingHistory = object : HabitHistoryRepository by history {
            override suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>) {
                history.scheduleHabit(habitId, dates)
                throw IOException("Simulated failure")
            }
        }
        val end = start.plusDays(5)
        val endClock = Clock.fixed(end.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        try {
            EnsureHabitSchedulesUseCase(RoomHabitRepository(database, failingHistory), endClock)()
            error("Expected failure")
        } catch (_: IOException) {
            assertEquals(
                start.toEpochDay(),
                database.habitDao().getById(habit.id)!!.scheduledThroughEpochDay
            )
            assertEquals(1, history.observeDailyPerformance(end).first().size)
        }
        EnsureHabitSchedulesUseCase(repository, endClock)()
        assertEquals(6, history.observeDailyPerformance(end).first().size)
    }

    @Test
    fun lateCreationWakesHistoryObserver() = runTest(timeout = 15.seconds) {
        val nextDay = start.plusDays(1)
        val beforeMidnight = Clock.fixed(
            nextDay.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1),
            ZoneOffset.UTC
        )
        val afterMidnight = Clock.fixed(
            nextDay.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(1),
            ZoneOffset.UTC
        )
        val observe = ObserveJiveConditionUseCase(
            history,
            JiveConditionScorer(),
            EnsureHabitSchedulesUseCase(repository, afterMidnight)
        )
        val initialHistoryObserved = CompletableDeferred<Unit>()
        val updatedCondition = async {
            observe(nextDay)
                .onEach { initialHistoryObserved.complete(Unit) }
                .drop(1)
                .first()
        }
        initialHistoryObserved.await()

        val habit = CreateHabitUseCase(repository, beforeMidnight)(
            "Exercise",
            "",
            setOf(nextDay.dayOfWeek)
        )
        updatedCondition.await()

        assertEquals(start, habit.startDate)
        val planned = history.observeDailyPerformance(nextDay).first().single()
        assertEquals(nextDay, planned.date)
        assertEquals(1, planned.plannedHabitCount)
        assertTrue(repository.getPendingSchedules(nextDay).isEmpty())
    }

    @Test
    fun aDayNotSelectedCreatesDefinitionWithoutSchedulingTheFuture() = runTest {
        val habit =
            CreateHabitUseCase(repository, startClock)("Exercise", "", setOf(DayOfWeek.FRIDAY))
        assertNotNull(repository.getById(habit.id))
        assertTrue(history.observeDailyPerformance(start.plusDays(20)).first().isEmpty())
        assertEquals(
            start.toEpochDay(),
            database.habitDao().getById(habit.id)!!.scheduledThroughEpochDay
        )
    }
}
