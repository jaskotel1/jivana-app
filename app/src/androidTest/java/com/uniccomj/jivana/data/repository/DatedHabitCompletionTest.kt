package com.uniccomj.jivana.data.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.model.HabitWeekOutcome
import com.uniccomj.jivana.domain.repository.HabitCompletionRejection
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import com.uniccomj.jivana.domain.usecase.SetHabitCompletionUseCase
import com.uniccomj.jivana.domain.usecase.SettleWeeklyHabitUseCase
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DatedHabitCompletionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val monday = LocalDate.of(2026, 1, 5)
    private val sunday = monday.plusDays(6)
    private val clock = Clock.fixed(Instant.parse("2026-01-12T00:30:00Z"), ZoneOffset.UTC)
    private val weekly = HabitSchedule.WeeklyTarget(3)
    private lateinit var database: JivanaDatabase
    private lateinit var store: CompletionStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, JivanaDatabase::class.java).build()
        store = CompletionStore(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun weeklyDatesFeedSettlementAndCorrectionFromTheSameRoomObservation() = runTest {
        val habit = store.create(weekly, monday)
        val wednesday = monday.plusDays(2)
        for (date in listOf(sunday, monday, wednesday, monday)) {
            store.setCompletion(habit.id, date, true)
        }
        assertTrue(store.history.observeDailyPerformance(sunday).first().isEmpty())
        assertEquals(
            listOf(wednesday, sunday),
            store.history.observeCompletedDates(habit.id, wednesday, sunday).first()
        )

        val snapshots = Channel<List<LocalDate>>(Channel.UNLIMITED)
        backgroundScope.launch {
            store.history.observeCompletedDates(habit.id, monday, sunday).collect {
                snapshots.send(it)
            }
        }
        val storedCycle = checkNotNull(store.habits.observeCycle(habit.id).first())
        val cycle = storedCycle.calendar
        val progress = storedCycle.progressOn(sunday)
        val settle = SettleWeeklyHabitUseCase(clock)
        val fullWeek = snapshots.receive()
        assertEquals(listOf(monday, wednesday, sunday), fullWeek)
        val before = settle(cycle, weekly, fullWeek).settledWeeks.single()
        assertEquals(3, before.progress.completedDays)
        assertEquals(HabitWeekOutcome.SUCCESS, before.outcome)

        store.setCompletion(habit.id, sunday, false)
        val correctedWeek = snapshots.receive()
        assertEquals(listOf(monday, wednesday), correctedWeek)
        val after = settle(cycle, weekly, correctedWeek).settledWeeks.single()
        assertEquals(2, after.progress.completedDays)
        assertEquals(HabitWeekOutcome.FAILURE, after.outcome)
        assertEquals(before.progress.id, after.progress.id)
        assertEquals(storedCycle.id, after.progress.id.cycleId)
        assertEquals(progress, store.habits.observeCycle(habit.id).first()?.progressOn(sunday))
    }

    @Test
    fun dailyCompletesAnEligibleDayUsingTheExistingScheduleGenerator() = runTest {
        val habit = store.create(HabitSchedule.Daily, monday)
        val wednesday = monday.plusDays(2)

        assertRejected(HabitCompletionRejection.UNSCHEDULED) {
            store.history.setHabitCompleted(habit.id, wednesday, true)
        }
        store.setCompletion(habit.id, wednesday, true)
        val before = store.history.observeDailyPerformance(wednesday).first()
        assertEquals(listOf(monday, monday.plusDays(1), wednesday), before.map { it.date })
        assertEquals(listOf(0, 0, 1), before.map { it.completedHabitCount })

        store.setCompletion(habit.id, wednesday, false)
        val corrected = store.history.observeDailyPerformance(wednesday).first()
        assertEquals(before.take(2), corrected.take(2))
        assertEquals(1, corrected.last().plannedHabitCount)
        assertEquals(0, corrected.last().completedHabitCount)
        assertTrue(corrected.last().activityRecorded)
        assertEquals(3, store.cycle(habit.id).progressOn(wednesday).activeDays)
    }

    @Test
    fun legacyScheduleRequiresAPlannedDayAndOldHistoryCanStillBeCorrected() = runTest {
        val schedule = HabitSchedule.LegacyWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val habit = store.create(schedule, monday)
        val friday = monday.plusDays(4)
        val tuesday = monday.plusDays(1)

        store.setCompletion(habit.id, friday, true)
        assertRejected(HabitCompletionRejection.UNSCHEDULED) {
            store.setCompletion(habit.id, tuesday, true)
        }
        assertEquals(listOf(friday), store.completedDates(habit.id, monday, sunday))
        assertEquals(2, store.cycle(habit.id).progressOn(tuesday).activeDays)

        store.history.scheduleHabit("legacy", setOf(monday))
        store.setCompletion("legacy", monday, true)
        assertEquals(listOf(monday), store.completedDates("legacy", monday, sunday))
        store.setCompletion("legacy", monday, false)
        assertEquals(emptyList<LocalDate>(), store.completedDates("legacy", monday, sunday))
        assertEquals(listOf(friday), store.completedDates(habit.id, monday, sunday))
        assertRejected(HabitCompletionRejection.NOT_FOUND) {
            store.setCompletion("unknown", monday, true)
        }
    }

    @Test
    fun futureAndPreStartDatesCannotBeCompleted() = runTest {
        val today = LocalDate.now(clock)
        val futureStart = today.plusDays(2)
        val habit = store.create(weekly, futureStart)

        assertRejected(HabitCompletionRejection.FUTURE_DATE) {
            store.setCompletion(habit.id, futureStart, true)
        }
        assertRejected(HabitCompletionRejection.BEFORE_START) {
            store.setCompletion(habit.id, today, true)
        }
        assertEquals(emptyList<LocalDate>(), store.completedDates(habit.id, monday, futureStart))
    }

    @Test
    fun archivingPreservesCompletionsAndRejectsSubsequentEdits() = runTest {
        val habit = store.create(weekly, monday)
        store.setCompletion(habit.id, monday, true)
        store.habits.archive(habit.id, ZonedDateTime.now(clock))

        assertRejected(HabitCompletionRejection.ARCHIVED) {
            store.setCompletion(habit.id, monday, false)
        }
        assertRejected(HabitCompletionRejection.ARCHIVED) {
            store.setCompletion(habit.id, sunday, true)
        }
        assertEquals(listOf(monday), store.completedDates(habit.id, monday, sunday))
    }

    @Test
    fun archivalAndCompletionRacingHaveAConsistentOutcome() = runTest {
        val habit = store.create(weekly, monday)
        val go = CompletableDeferred<Unit>()
        val archive = async {
            go.await()
            store.habits.archive(habit.id, ZonedDateTime.now(clock))
        }
        val completion = async {
            go.await()
            runCatching { store.setCompletion(habit.id, monday, true) }
        }
        go.complete(Unit)

        val write = completion.await()
        archive.await()
        val dates = store.completedDates(habit.id, monday, sunday)
        if (write.isSuccess) {
            assertEquals(listOf(monday), dates)
        } else {
            val rejection = write.exceptionOrNull() as? HabitOperationException.CompletionRejected
            assertEquals(HabitCompletionRejection.ARCHIVED, rejection?.reason)
            assertTrue(dates.isEmpty())
        }
    }

    @Test
    fun concurrentRetriesEmitOnceAndUndoThenRedoChangesTheStoredState() = runTest {
        val habit = store.create(weekly, monday)
        var reactions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.events.completions.collect { reactions++ }
        }
        val go = CompletableDeferred<Unit>()
        val requests = List(8) {
            async {
                go.await()
                store.setCompletion(habit.id, monday, true)
            }
        }
        go.complete(Unit)
        requests.awaitAll()
        assertEquals(1, reactions)
        assertEquals(listOf(monday), store.completedDates(habit.id, monday, sunday))

        repeat(2) { store.setCompletion(habit.id, monday, false) }
        assertEquals(1, reactions)
        assertEquals(emptyList<LocalDate>(), store.completedDates(habit.id, monday, sunday))

        repeat(2) { store.setCompletion(habit.id, monday, true) }
        assertEquals(2, reactions)
        assertEquals(listOf(monday), store.completedDates(habit.id, monday, sunday))
    }

    @Test
    fun failingInteractionInsertRollsBackTheCompletionAndEmitsNoReaction() = runTest {
        val habit = store.create(weekly, monday)
        var reactions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.events.completions.collect { reactions++ }
        }
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_interaction BEFORE INSERT ON habit_day_interactions
            BEGIN
                SELECT RAISE(ABORT, 'forced completion rollback');
            END
            """.trimIndent()
        )

        val failure = runCatching { store.setCompletion(habit.id, monday, true) }.exceptionOrNull()
        assertTrue(failure is SQLiteException)
        assertEquals(emptyList<LocalDate>(), store.completedDates(habit.id, monday, sunday))
        assertEquals(0, reactions)
    }

    @Test
    fun storedDatesAndCorrectionsSurviveReopenAndADeviceZoneChange() = runTest {
        val name = "dated-completions-${UUID.randomUUID()}.db"
        val nextMonday = monday.plusWeeks(1)
        val westernClock = clock.withZone(ZoneId.of("America/Los_Angeles"))
        try {
            val cycle = withDatabase(name) { first ->
                val original = CompletionStore(first, clock)
                val habit = original.create(weekly, monday)
                original.setCompletion(habit.id, sunday, true)
                original.setCompletion(habit.id, nextMonday, true)
                original.cycle(habit.id)
            }
            val habitId = cycle.habitId

            withDatabase(name) { second ->
                val reopened = CompletionStore(second, westernClock)
                assertEquals(cycle, reopened.cycle(habitId))
                assertEquals(
                    7,
                    reopened.cycle(habitId).progressOn(LocalDate.now(westernClock)).activeDays
                )
                assertEquals(
                    listOf(sunday, nextMonday),
                    reopened.completedDates(habitId, monday, nextMonday)
                )
                assertRejected(HabitCompletionRejection.FUTURE_DATE) {
                    reopened.setCompletion(habitId, nextMonday, false)
                }
                reopened.setCompletion(habitId, sunday, false)
            }

            withDatabase(name) { third ->
                val reopened = CompletionStore(third, clock)
                assertEquals(cycle, reopened.cycle(habitId))
                assertEquals(8, reopened.cycle(habitId).progressOn(LocalDate.now(clock)).activeDays)
                val dates = reopened.completedDates(habitId, monday, nextMonday)
                assertEquals(listOf(nextMonday), dates)
                reopened.setCompletion(habitId, sunday, true)
                assertEquals(
                    listOf(sunday, nextMonday),
                    reopened.completedDates(habitId, monday, nextMonday)
                )
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun pauseIntervalsAndArchivedCycleSurviveReopen() = runTest {
        val name = "habit-cycle-${UUID.randomUUID()}.db"
        val pause = monday.plusDays(25)
        val resume = pause.plusDays(7)
        try {
            val original = withDatabase(name) { db ->
                val habits = CompletionStore(db, clock)
                val habit = habits.create(weekly, monday)
                val cycle = habits.cycle(habit.id)
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO habit_cycle_pauses VALUES (?, ?, NULL)",
                    arrayOf<Any>(cycle.id, pause.toEpochDay())
                )
                cycle
            }
            withDatabase(name) { db ->
                val habits = CompletionStore(db, clock)
                val paused = habits.cycle(original.habitId)
                assertEquals(25, paused.progressOn(resume.minusDays(1)).activeDays)
                assertEquals(null, paused.calendar.endDate)
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE habit_cycle_pauses SET resumeEpochDay = ? WHERE cycleId = ?",
                    arrayOf<Any>(resume.toEpochDay(), paused.id)
                )
                val archive = resume.plusDays(1).atStartOfDay(ZoneOffset.UTC)
                habits.habits.archive(original.habitId, archive)
            }
            withDatabase(name) { db ->
                val reopened = CompletionStore(db, clock).cycle(original.habitId)
                assertEquals(original.id, reopened.id)
                assertEquals(26, reopened.progressOn(resume).activeDays)
                assertEquals(27, reopened.progressOn(resume.plusWeeks(1)).activeDays)
                assertEquals(resume.plusDays(1), reopened.archivedOn)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private inline fun <T> withDatabase(name: String, block: (JivanaDatabase) -> T): T {
        val opened = Room.databaseBuilder(context, JivanaDatabase::class.java, name).build()
        try {
            return block(opened)
        } finally {
            opened.close()
        }
    }

    private suspend fun assertRejected(
        reason: HabitCompletionRejection,
        operation: suspend () -> Unit
    ) {
        val error = runCatching { operation() }.exceptionOrNull()
        assertEquals(reason, (error as? HabitOperationException.CompletionRejected)?.reason)
    }

    private class CompletionStore(database: JivanaDatabase, private val clock: Clock) {
        val events = HabitCompletionEvents()
        val history = RoomHabitHistoryRepository(database, JiveScoringConfig(), events, clock)
        val habits = RoomHabitRepository(database, history)
        val setCompletion = SetHabitCompletionUseCase(
            habits,
            history,
            EnsureHabitSchedulesUseCase(habits, clock),
            clock
        )

        suspend fun create(schedule: HabitSchedule, date: LocalDate): Habit =
            CreateHabitUseCase(habits, clock)
                .createWithSchedule("Habit", "", schedule, startDate = date)

        suspend fun completedDates(id: String, from: LocalDate, through: LocalDate) =
            history.observeCompletedDates(id, from, through).first()

        suspend fun cycle(habitId: String) = checkNotNull(habits.observeCycle(habitId).first())
    }
}
