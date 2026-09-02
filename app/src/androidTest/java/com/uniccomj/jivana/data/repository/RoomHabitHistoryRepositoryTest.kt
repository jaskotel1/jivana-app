package com.uniccomj.jivana.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveSleepiness
import com.uniccomj.jivana.domain.usecase.JiveConditionScorer
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHabitHistoryRepositoryTest {
    private lateinit var database: JivanaDatabase
    private lateinit var repository: RoomHabitHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JivanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomHabitHistoryRepository(database.habitHistoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dailyPerformanceReflectsPlannedCompletedAndConsciousActivity() = runTest {
        val date = LocalDate.of(2026, 1, 1)
        scheduleHabits(count = 4, date = date)

        repository.setHabitCompleted("habit-1", date, completed = true)

        val performance = repository.observeDailyPerformance(date).first().single()
        assertEquals(4, performance.plannedHabitCount)
        assertEquals(1, performance.completedHabitCount)
        assertEquals(0.25, performance.completionRate)
        assertEquals(true, performance.activityRecorded)
    }

    @Test
    fun allCompletedHabitsProduceOneHundredPercentAndRecordedActivity() = runTest {
        val date = LocalDate.of(2026, 1, 1)
        scheduleHabits(count = 4, date = date)
        repeat(4) { index ->
            repository.setHabitCompleted("habit-$index", date, completed = true)
        }

        val performance = repository.observeDailyPerformance(date).first().single()
        assertEquals(1.0, performance.completionRate)
        assertEquals(true, performance.activityRecorded)
    }

    @Test
    fun plannedDayWithoutCompletionRecordsRemainsInHistory() = runTest {
        val date = LocalDate.of(2026, 1, 1)
        scheduleHabits(count = 4, date = date)

        val performance = repository.observeDailyPerformance(date).first().single()
        assertEquals(0.0, performance.completionRate)
        assertEquals(false, performance.activityRecorded)
    }

    @Test
    fun consciousZeroPercentIsPreservedAfterCompletionIsUndone() = runTest {
        val date = LocalDate.of(2026, 1, 1)
        scheduleHabits(count = 4, date = date)

        repository.setHabitCompleted("habit-1", date, completed = true)
        repository.setHabitCompleted("habit-1", date, completed = false)

        val performance = repository.observeDailyPerformance(date).first().single()
        assertEquals(0.0, performance.completionRate)
        assertEquals(true, performance.activityRecorded)
    }

    @Test
    fun explicitCheckInRecordsConsciousZeroPercent() = runTest {
        val date = LocalDate.of(2026, 1, 1)
        scheduleHabits(count = 4, date = date)

        repository.recordDailyCheckIn(date)

        val performance = repository.observeDailyPerformance(date).first().single()
        assertEquals(0.0, performance.completionRate)
        assertEquals(true, performance.activityRecorded)
    }

    @Test
    fun noScheduledHabitsProducesNoScoredDay() = runTest {
        val date = LocalDate.of(2026, 1, 1)

        assertEquals(
            emptyList<DailyHabitPerformance>(),
            repository.observeDailyPerformance(date).first()
        )
    }

    @Test
    fun unscheduledDayInsideHistoryHasNullCompletionRateAndIsNeutral() = runTest {
        scheduleHabits(count = 4, date = StartDate)
        scheduleHabits(count = 2, date = StartDate.plusDays(2))

        val history = repository.observeDailyPerformance(StartDate.plusDays(2)).first()

        assertEquals(3, history.size)
        assertEquals(4, history[0].plannedHabitCount)
        assertEquals(null, history[1].completionRate)
        assertEquals(2, history[2].plannedHabitCount)
    }

    @Test
    fun mixedMultiDayHistoryPreservesEveryScheduledDay() = runTest {
        val fullDay = StartDate
        val partialDay = StartDate.plusDays(1)
        val missedDay = StartDate.plusDays(2)
        listOf(fullDay, partialDay, missedDay).forEach { date ->
            scheduleHabits(count = 4, date = date)
        }
        repeat(4) { index ->
            repository.setHabitCompleted("habit-$index", fullDay, completed = true)
        }
        repository.setHabitCompleted("habit-0", partialDay, completed = true)

        val history = repository.observeDailyPerformance(missedDay).first()

        assertEquals(listOf(1.0, 0.25, 0.0), history.map { it.completionRate })
        assertEquals(listOf(true, true, false), history.map { it.activityRecorded })
    }

    @Test
    fun fourteenConsciousZeroPercentDaysDoNotCauseSleeping() = runTest {
        val history = createFourteenDayHistory(activityRecorded = true)

        assertEquals(JiveSleepiness.VERY_SLEEPY, JiveConditionScorer().score(history).sleepiness)
    }

    @Test
    fun fourteenInactiveScheduledDaysCauseSleeping() = runTest {
        val history = createFourteenDayHistory(activityRecorded = false)

        assertEquals(JiveSleepiness.SLEEPING, JiveConditionScorer().score(history).sleepiness)
    }

    private suspend fun createFourteenDayHistory(
        activityRecorded: Boolean
    ): List<DailyHabitPerformance> {
        repeat(14) { dayIndex ->
            val date = StartDate.plusDays(dayIndex.toLong())
            scheduleHabits(count = 4, date = date)
            if (activityRecorded) repository.recordDailyCheckIn(date)
        }
        return repository.observeDailyPerformance(StartDate.plusDays(13)).first()
    }

    private suspend fun scheduleHabits(count: Int, date: LocalDate) {
        repeat(count) { index ->
            repository.scheduleHabit("habit-$index", setOf(date))
        }
    }

    private companion object {
        val StartDate: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
