package com.uniccomj.jivana.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.HabitCyclePause
import com.uniccomj.jivana.domain.model.HabitCycleStatus
import com.uniccomj.jivana.domain.model.HabitPausePolicy
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.model.HabitWeekOutcome
import com.uniccomj.jivana.domain.repository.HabitCompletionRejection
import com.uniccomj.jivana.domain.repository.HabitOperationException.CompletionRejected
import com.uniccomj.jivana.domain.repository.HabitOperationException.PauseChangeRejected
import com.uniccomj.jivana.domain.repository.HabitPauseRejection
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import com.uniccomj.jivana.domain.usecase.SetHabitCompletionUseCase
import com.uniccomj.jivana.domain.usecase.SettleWeeklyHabitUseCase
import com.uniccomj.jivana.domain.usecase.TrackHabitCycleUseCase
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitPauseRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = LocalDate.of(2026, 12, 6)

    @Test
    fun sevenDayPausePersistsAcrossMonthYearDatabaseReopenAndZoneChange() = runTest {
        val name = "habit-pause-${UUID.randomUUID()}.db"
        val pauseStart = start.plusDays(25)
        val resumeDate = pauseStart.plusDays(7)
        val previous = pauseStart.minusDays(1)
        try {
            val habit = withDatabase(name) { db ->
                val store = Store(db, clockOn(pauseStart))
                val habit = store.create(start, HabitSchedule.Daily)
                store.complete(habit.id, previous, true)
                store.track.pause(habit.id)
                habit
            }
            withDatabase(name) { db ->
                val clock = clockOn(resumeDate).withZone(ZoneId.of("America/Los_Angeles"))
                val store = Store(db, clock)
                val paused = store.cycle(habit.id)
                val lastPausedDay = LocalDate.now(clock)
                val progress = paused.progressOn(lastPausedDay)
                assertEquals(resumeDate.minusDays(1), lastPausedDay)
                assertEquals(HabitCycleStatus.PAUSED, progress.status)
                assertEquals(25, progress.activeDays)
                assertEquals(7L, progress.pause?.days)
                assertNull(paused.calendar.endDate)
                store.ensure()
                val performance = store.history.observeDailyPerformance(lastPausedDay).first()
                assertEquals(previous, performance.last().date)
                assertTrue(performance.none { it.date >= pauseStart })
                assertEquals(listOf(previous), store.completed(habit.id, start, resumeDate))
                assertEquals(1, performance.last().completedHabitCount)
            }
            withDatabase(name) { db ->
                val store = Store(db, clockOn(resumeDate))
                store.track.resume(habit.id, pauseStart)
                val resumed = store.cycle(habit.id)
                assertEquals(listOf(HabitCyclePause(pauseStart, resumeDate)), resumed.pauses)
                assertEquals(26, resumed.progressOn(resumeDate).activeDays)
                assertEquals(start.plusDays(66), resumed.calendar.endDate)
                store.complete(habit.id, resumeDate, true)
                assertEquals(
                    listOf(previous, resumeDate),
                    store.completed(habit.id, start, resumeDate)
                )
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun legacyWeekdaysSkipAPausedFridayAndResumeOnTheNextPlannedDay() = runTest {
        withDatabase { db ->
            val monday = start.plusDays(1)
            val friday = monday.plusDays(4)
            val nextMonday = monday.plusWeeks(1)
            val schedule = HabitSchedule.LegacyWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            val store = Store(db, clockOn(friday))
            val habit = store.create(monday, schedule)
            store.complete(habit.id, monday, true)
            store.ensure()
            store.track.pause(habit.id)
            assertPauseBlocksCompletion(store, habit, friday)
            assertEquals(
                listOf(monday),
                store.history.observeDailyPerformance(friday).first().map {
                    it.date
                }
            )
            val resumed = Store(db, clockOn(nextMonday))
            resumed.track.resume(habit.id, friday)
            resumed.complete(habit.id, nextMonday, true)
            assertEquals(
                listOf(monday, nextMonday),
                resumed.completed(habit.id, monday, nextMonday)
            )
            val after = resumed.history.observeDailyPerformance(nextMonday).first()
            assertEquals(listOf(monday, nextMonday), after.map { it.date })
        }
    }

    @Test
    fun storedWeeklyPauseRecomputesTargetsAndOmitsAWholePausedWeek() = runTest {
        withDatabase { db ->
            val monday = start.plusDays(1)
            val friday = monday.plusDays(4)
            val resume = monday.plusWeeks(3).minusDays(1)
            val schedule = HabitSchedule.WeeklyTarget(3)
            val store = Store(db, clockOn(friday))
            val habit = store.create(monday, schedule)
            store.complete(habit.id, monday, true)
            store.complete(habit.id, monday.plusDays(1), true)
            store.track.pause(habit.id)
            assertPauseBlocksCompletion(store, habit, friday)
            val resumed = Store(db, clockOn(resume))
            resumed.track.resume(habit.id, friday)
            resumed.complete(habit.id, resume, true)
            val cycle = resumed.cycle(habit.id).calendar
            val settle = SettleWeeklyHabitUseCase(clockOn(resume))
            val dates = resumed.completed(habit.id, monday, resume)
            val first = settle(cycle, schedule, dates, resume).settledWeeks.single()
            assertEquals(HabitWeekOutcome.SUCCESS, first.outcome)
            assertEquals(4, first.progress.activeDays)
            assertEquals(2, first.progress.effectiveTarget)
            val settled = settle(cycle, schedule, dates, resume.plusDays(1)).settledWeeks
            assertEquals(listOf(monday, monday.plusWeeks(2)), settled.map { it.progress.id.monday })
            assertEquals(1, settled.last().progress.effectiveTarget)
            val corrected = settle(cycle, schedule, dates.drop(1), resume.plusDays(1)).settledWeeks
            assertEquals(settled.map { it.progress.id }, corrected.map { it.progress.id })
            assertEquals(HabitWeekOutcome.FAILURE, corrected.first().outcome)
        }
    }

    @Test
    fun oneDayPauseAndConcurrentRetriesConsumeOnlyOneSlotAndLateResumeCannotCloseAnotherPause() =
        runTest {
            withDatabase { db ->
                val pause = start.plusDays(2)
                val resumed = pause.plusDays(1)
                val store = Store(db, clockOn(pause))
                val habit = store.create(start, HabitSchedule.Daily)
                List(3) { async { store.track.pause(habit.id) } }.awaitAll()
                assertEquals(listOf(HabitCyclePause(pause)), store.cycle(habit.id).pauses)
                val nextDay = Store(db, clockOn(resumed))
                List(3) { async { nextDay.track.resume(habit.id, pause) } }.awaitAll()
                val after = nextDay.cycle(habit.id)
                assertEquals(listOf(HabitCyclePause(pause, resumed)), after.pauses)
                assertEquals(start.plusDays(60), after.calendar.endDate)
                val second = resumed.plusDays(1)
                val later = Store(db, clockOn(second))
                listOf(
                    async { later.track.pause(habit.id) },
                    async { later.track.resume(habit.id, pause) }
                ).awaitAll()
                assertEquals(HabitCyclePause(second), later.cycle(habit.id).pauses.last())
                later.track.resume(habit.id, second)
                assertRejected(HabitPauseRejection.LIMIT_REACHED) {
                    Store(db, clockOn(second.plusDays(1))).track.pause(habit.id)
                }
                assertEquals(2, later.cycle(habit.id).pauses.size)
            }
        }

    @Test
    fun rejectsPausesBeforeStartAfterCompletionAndAfterArchive() = runTest {
        withDatabase { db ->
            val store = Store(db, clockOn(start.minusDays(1)))
            val habit = store.create(start, HabitSchedule.Daily)
            assertRejected(HabitPauseRejection.INACTIVE_CYCLE) { store.track.pause(habit.id) }
            val later = Store(db, clockOn(start.plusDays(60)))
            assertRejected(HabitPauseRejection.INACTIVE_CYCLE) { later.track.pause(habit.id) }
            store.habits.archive(habit.id, start.atStartOfDay(ZoneOffset.UTC))
            assertRejected(HabitPauseRejection.INACTIVE_CYCLE) {
                Store(db, clockOn(start)).track.pause(habit.id)
            }
            assertTrue(store.cycle(habit.id).pauses.isEmpty())
        }
    }

    @Test
    fun rejectsPauseOnARecordedCompletionAndProtectsLaterDatesAfterClockMovesBack() = runTest {
        withDatabase { db ->
            val date = start.plusDays(1)
            val store = Store(db, clockOn(date))
            val habit = store.create(start, HabitSchedule.WeeklyTarget(2))
            store.complete(habit.id, date, true)
            assertRejected(HabitPauseRejection.COMPLETION_EXISTS) { store.track.pause(habit.id) }
            assertRejected(HabitPauseRejection.COMPLETION_EXISTS) {
                Store(db, clockOn(start)).track.pause(habit.id)
            }
            assertEquals(listOf(date), store.completed(habit.id, start, date))
            assertTrue(store.cycle(habit.id).pauses.isEmpty())
        }
    }

    @Test
    fun concurrentPauseAndCompletionCannotBothCommit() = runTest {
        withDatabase { db ->
            val store = Store(db, clockOn(start))
            val habit = store.create(start, HabitSchedule.Daily)
            val pause = async { runCatching { store.track.pause(habit.id) } }
            val completion = async { runCatching { store.complete(habit.id, start, true) } }
            val pauseResult = pause.await()
            val completionResult = completion.await()
            assertTrue(pauseResult.isSuccess xor completionResult.isSuccess)
            if (pauseResult.isSuccess) {
                val failure = completionResult.exceptionOrNull() as CompletionRejected
                assertEquals(HabitCompletionRejection.PAUSED, failure.reason)
                assertTrue(store.completed(habit.id, start, start).isEmpty())
            } else {
                val failure = pauseResult.exceptionOrNull() as PauseChangeRejected
                assertEquals(HabitPauseRejection.COMPLETION_EXISTS, failure.reason)
                assertEquals(listOf(start), store.completed(habit.id, start, start))
                assertTrue(store.cycle(habit.id).pauses.isEmpty())
            }
        }
    }

    private suspend fun assertPauseBlocksCompletion(store: Store, habit: Habit, date: LocalDate) {
        val error = runCatching { store.complete(habit.id, date, true) }.exceptionOrNull()
        assertEquals(
            HabitCompletionRejection.PAUSED,
            (error as? CompletionRejected)?.reason
        )
    }

    private suspend fun assertRejected(reason: HabitPauseRejection, action: suspend () -> Unit) {
        val error = runCatching { action() }.exceptionOrNull()
        assertEquals(reason, (error as? PauseChangeRejected)?.reason)
    }

    private fun clockOn(date: LocalDate): Clock =
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private inline fun <T> withDatabase(name: String? = null, block: (JivanaDatabase) -> T): T {
        val builder = if (name == null) {
            Room.inMemoryDatabaseBuilder(context, JivanaDatabase::class.java)
        } else {
            Room.databaseBuilder(context, JivanaDatabase::class.java, name)
        }
        val db = builder.build()
        try {
            return block(db)
        } finally {
            db.close()
        }
    }

    private class Store(db: JivanaDatabase, private val clock: Clock) {
        val history =
            RoomHabitHistoryRepository(db, JiveScoringConfig(), HabitCompletionEvents(), clock)
        val habits = RoomHabitRepository(db, history)
        private val cycles = RoomHabitCycleRepository(db)
        val track = TrackHabitCycleUseCase(cycles, clock, HabitPausePolicy.Default)
        val ensure = EnsureHabitSchedulesUseCase(habits, clock)
        val complete = SetHabitCompletionUseCase(habits, history, ensure, clock)

        suspend fun create(start: LocalDate, schedule: HabitSchedule): Habit =
            CreateHabitUseCase(habits, clock)
                .createWithSchedule("Habit", "", schedule, startDate = start)

        suspend fun cycle(id: String) = checkNotNull(cycles.observeCycle(id).first())

        suspend fun completed(id: String, from: LocalDate, through: LocalDate) =
            history.observeCompletedDates(id, from, through).first()
    }
}
