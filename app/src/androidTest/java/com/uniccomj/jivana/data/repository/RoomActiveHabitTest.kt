package com.uniccomj.jivana.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.mapper.toEntity
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import com.uniccomj.jivana.domain.usecase.JiveConditionScorer
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import com.uniccomj.jivana.domain.usecase.ManageActiveHabitsUseCase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomActiveHabitTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = LocalDate.of(2026, 11, 5)
    private val archiveDay = start.plusDays(2)

    @Test
    fun limitRejectsDirectWritesButAllowsIdenticalRetriesBeforeAndAfterArchival() = runTest {
        inMemoryDatabase().useDatabase { database ->
            val repository = repository(database)
            val create = CreateHabitUseCase(repository, clockOn(start))
            val first = create.createWithSchedule("Water", "", HabitSchedule.Daily, "first")
            assertEquals(listOf(first), repository.observeActiveHabits().first())
            val directWrite = runCatching {
                repository.create(first.copy(id = "second"), setOf(start))
            }
            assertTrue(
                directWrite.exceptionOrNull() is HabitOperationException.ActiveHabitLimitReached
            )
            assertNull(repository.getById("second"))
            assertEquals(
                first,
                create.createWithSchedule("Water", "", HabitSchedule.Daily, "first")
            )

            val wrongToken = runCatching {
                create.createWithSchedule("Water", "Different", HabitSchedule.Daily, "first")
            }
            assertTrue(wrongToken.exceptionOrNull() is HabitOperationException.RequestTokenConflict)
            val archived = repository.archive(first.id, archiveDay.atStartOfDay(ZoneOffset.UTC))
            val second = create.createWithSchedule("Read", "", HabitSchedule.Daily, "second")
            assertEquals(
                archived,
                create.createWithSchedule("Water", "", HabitSchedule.Daily, "first")
            )
            assertEquals(listOf(second), repository.observeActiveHabits().first())
        }
    }

    @Test
    fun differentTokensCompetingForTheLastSlotCommitExactlyOneHabitAndPlan() = runTest {
        inMemoryDatabase().useDatabase { database ->
            val history = history(database)
            val repository = RoomHabitRepository(database, history)
            val create = CreateHabitUseCase(repository, clockOn(start))
            val startTogether = CompletableDeferred<Unit>()
            val requests = listOf("Water", "Read").map { name ->
                async {
                    startTogether.await()
                    runCatching { create.createWithSchedule(name, "", HabitSchedule.Daily, name) }
                }
            }
            startTogether.complete(Unit)
            val results = requests.awaitAll()
            val created = results.mapNotNull { it.getOrNull() }.single()
            val error = results.mapNotNull { it.exceptionOrNull() }.single()
            assertTrue(error is HabitOperationException.ActiveHabitLimitReached)
            assertEquals(listOf(created), repository.observeActiveHabits().first())
            assertEquals(
                1,
                history.observeDailyPerformance(start).first().single().plannedHabitCount
            )
        }
    }

    @Test
    fun observerSeesCreationArchivalAndReplacement() = runTest(timeout = 20.seconds) {
        inMemoryDatabase().useDatabase { database ->
            val repository = repository(database)
            val create = CreateHabitUseCase(repository, clockOn(start))
            val updates = Channel<List<Habit>>(Channel.UNLIMITED)
            val collector =
                launch { repository.observeActiveHabits().collect { updates.send(it) } }
            try {
                assertEquals(emptyList<Habit>(), updates.receive())
                val first = create.createWithSchedule("Water", "", HabitSchedule.Daily)
                assertEquals(listOf(first), updates.receive())
                repository.archive(first.id, archiveDay.atStartOfDay(ZoneOffset.UTC))
                assertEquals(emptyList<Habit>(), updates.receive())
                val replacement = create.createWithSchedule(
                    "Read",
                    "",
                    HabitSchedule.WeeklyTarget(3)
                )
                assertEquals(listOf(replacement), updates.receive())
            } finally {
                collector.cancel()
            }
        }
    }

    @Test
    fun archivePreservesHistoryAndScoringButCancelsOutstandingFuturePlans() = runTest {
        inMemoryDatabase().useDatabase { database ->
            val history = history(database)
            val repository = RoomHabitRepository(database, history)
            val create = CreateHabitUseCase(repository, clockOn(start))
            val habit = create.createWithSchedule("Water", "", HabitSchedule.Daily)
            val manage = ManageActiveHabitsUseCase(
                repository,
                EnsureHabitSchedulesUseCase(repository, clockOn(archiveDay)),
                clockOn(archiveDay)
            )
            val missedAfterArchive = archiveDay.plusDays(1)
            val completedAfterClockRollback = archiveDay.plusDays(2)
            history.setHabitCompleted(habit.id, start, true)
            history.recordDailyCheckIn(start.plusDays(1))
            history.scheduleHabit(habit.id, setOf(missedAfterArchive, completedAfterClockRollback))
            history.setHabitCompleted(habit.id, completedAfterClockRollback, true)
            EnsureHabitSchedulesUseCase(repository, clockOn(archiveDay))()
            val inFlight = repository.getPendingSchedules(missedAfterArchive).single()
            val before = history.observeDailyPerformance(archiveDay).first()
            val scorer = JiveConditionScorer()

            manage.archive(habit.id)
            val archived = repository.getById(habit.id)!!
            assertEquals(habit.copy(archivedAt = clockOn(archiveDay).instant()), archived)
            assertEquals(
                archived,
                repository.archive(habit.id, archiveDay.plusDays(7).atStartOfDay(ZoneOffset.UTC))
            )
            assertEquals(before, history.observeDailyPerformance(archiveDay).first())
            assertEquals(
                scorer.score(before),
                scorer.score(history.observeDailyPerformance(archiveDay).first())
            )
            assertEquals(1, before.single { it.date == start }.completedHabitCount)
            assertTrue(before.single { it.date == start.plusDays(1) }.activityRecorded)
            assertFalse(
                repository.advanceSchedule(inFlight, missedAfterArchive, setOf(missedAfterArchive))
            )
            assertFalse(EnsureHabitSchedulesUseCase(repository, clockOn(archiveDay.plusWeeks(1)))())

            val dates = database.habitHistoryDao()
                .observeDailyPerformance(start.toEpochDay(), archiveDay.plusWeeks(1).toEpochDay())
                .first().associateBy { LocalDate.ofEpochDay(it.dateEpochDay) }
            assertEquals(
                setOf(start, start.plusDays(1), archiveDay, completedAfterClockRollback),
                dates.keys
            )
            assertEquals(1, dates.getValue(completedAfterClockRollback).completedHabitCount)
        }
    }

    @Test
    fun oldConflictSurvivesReopeningAndOnlyAnExplicitCurrentSelectionArchivesHabits() = runTest {
        val name = "legacy-active-${UUID.randomUUID()}.db"
        val habits = listOf("water", "exercise", "read").map {
            Habit(it, it, null, HabitSchedule.Daily, start)
        }
        try {
            openDatabase(name).useDatabase { database ->
                val history = history(database)
                for (habit in habits) {
                    database.habitDao().insert(habit.toEntity(start.plusDays(1)))
                    history.scheduleHabit(habit.id, setOf(start, start.plusDays(1)))
                }
                history.setHabitCompleted(habits.first().id, start, true)
                history.recordDailyCheckIn(start.plusDays(1))
            }
            openDatabase(name).useDatabase { database ->
                verifyLegacyResolution(database, habits)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun verifyLegacyResolution(database: JivanaDatabase, habits: List<Habit>) {
        val repository = repository(database)
        val ids = habits.map { it.id }.toSet()
        assertEquals(ids, repository.observeActiveHabits().first().map { it.id }.toSet())
        val newHabit = runCatching {
            CreateHabitUseCase(repository, clockOn(start))
                .createWithSchedule("New", "", HabitSchedule.Daily)
        }
        assertTrue(newHabit.exceptionOrNull() is HabitOperationException.ActiveHabitLimitReached)
        val history = history(database)
        val before = history.observeDailyPerformance(archiveDay).first()
        val keep = habits[1]
        val archivedAt = archiveDay.atStartOfDay(ZoneOffset.UTC)
        val stale = runCatching {
            repository.resolveActiveHabitConflict(keep.id, ids - habits.last().id, archivedAt)
        }
        assertTrue(stale.exceptionOrNull() is HabitOperationException.ActiveHabitsChanged)
        assertEquals(ids, repository.observeActiveHabits().first().map { it.id }.toSet())

        repository.resolveActiveHabitConflict(keep.id, ids, archivedAt)
        assertEquals(listOf(keep), repository.observeActiveHabits().first())
        for (habit in habits - keep) {
            assertEquals(
                habit.copy(archivedAt = archivedAt.toInstant()),
                repository.getById(habit.id)
            )
        }
        assertEquals(before, history.observeDailyPerformance(archiveDay).first())
        assertEquals(3, before.single { it.date == start }.plannedHabitCount)
        assertEquals(1, before.single { it.date == start }.completedHabitCount)
        assertTrue(before.single { it.date == start.plusDays(1) }.activityRecorded)
    }

    private fun inMemoryDatabase(): JivanaDatabase =
        Room.inMemoryDatabaseBuilder(context, JivanaDatabase::class.java).build()

    private fun openDatabase(name: String): JivanaDatabase =
        Room.databaseBuilder(context, JivanaDatabase::class.java, name).build()

    private inline fun JivanaDatabase.useDatabase(block: (JivanaDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun repository(database: JivanaDatabase) =
        RoomHabitRepository(database, history(database))

    private fun history(database: JivanaDatabase) = RoomHabitHistoryRepository(
        database,
        JiveScoringConfig(),
        HabitCompletionEvents(),
        clockOn(archiveDay.plusDays(2))
    )

    private fun clockOn(date: LocalDate): Clock =
        Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
}
