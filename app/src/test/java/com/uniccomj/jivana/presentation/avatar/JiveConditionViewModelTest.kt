package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.repository.HabitRepository
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import com.uniccomj.jivana.domain.usecase.JiveConditionScorer
import com.uniccomj.jivana.domain.usecase.ObserveJiveConditionUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JiveConditionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `existing history is scored after presentation layer is recreated`() = runTest(dispatcher) {
        val repository = FakeHabitHistoryRepository(inactiveHistory(dayCount = 14))

        val firstController = JiveConditionController(HabitCompletionEvents())
        createViewModel(repository, firstController)
        advanceUntilIdle()

        val recreatedController = JiveConditionController(HabitCompletionEvents())
        createViewModel(repository, recreatedController)
        advanceUntilIdle()

        assertEquals(firstController.condition.value, recreatedController.condition.value)
        assertEquals(JiveSleepiness.SLEEPING, recreatedController.condition.value.sleepiness)
    }

    @Test
    fun `history change is automatically rescored and published`() = runTest(dispatcher) {
        val repository = FakeHabitHistoryRepository(emptyList())
        val controller = JiveConditionController(HabitCompletionEvents())
        createViewModel(repository, controller)
        advanceUntilIdle()

        assertEquals(JiveCondition(), controller.condition.value)

        repository.replaceHistory(inactiveHistory(dayCount = 14))
        advanceUntilIdle()

        assertEquals(
            JiveCondition(
                mood = JiveMood.DEVASTATED,
                energy = JiveEnergy.EXHAUSTED,
                sleepiness = JiveSleepiness.SLEEPING
            ),
            controller.condition.value
        )
    }

    @Test
    fun `resume after date change observes history through the new day`() = runTest(dispatcher) {
        val repository = FakeHabitHistoryRepository(emptyList())
        val clock = MutableClock(Instant.parse("2026-01-31T12:00:00Z"))
        val viewModel = createViewModel(
            repository = repository,
            controller = JiveConditionController(HabitCompletionEvents()),
            clock = clock
        )
        advanceUntilIdle()

        clock.currentInstant = Instant.parse("2026-02-01T12:00:00Z")
        viewModel.refreshForCurrentDay()
        advanceUntilIdle()

        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1)),
            repository.observedDates
        )
    }

    @Test
    fun `foreground midnight refreshes history without another resume`() = runTest(dispatcher) {
        val repository = FakeHabitHistoryRepository(emptyList())
        val clock = MutableClock(Instant.parse("2026-12-31T23:59:59Z"))
        val vm =
            createViewModel(repository, JiveConditionController(HabitCompletionEvents()), clock)
        backgroundScope.launch { vm.refreshWhileResumed() }
        runCurrent()
        clock.currentInstant = Instant.parse("2027-01-01T00:00:01Z")
        advanceTimeBy(1001)
        runCurrent()
        assertEquals(LocalDate.of(2027, 1, 1), repository.observedDates.last())
    }

    @Test
    fun `foreground zone change refreshes the local date and resume refreshes even on same day`() =
        runTest(dispatcher) {
            val repository = FakeHabitHistoryRepository(emptyList())
            val clock = MutableClock(Instant.parse("2026-11-05T00:30:00Z"))
            val vm =
                createViewModel(repository, JiveConditionController(HabitCompletionEvents()), clock)
            backgroundScope.launch { vm.refreshWhileResumed() }
            runCurrent()
            clock.zoneId = ZoneId.of("America/Los_Angeles")
            advanceTimeBy(60_001)
            runCurrent()
            assertEquals(LocalDate.of(2026, 11, 4), repository.observedDates.last())
            val count = repository.observedDates.size
            vm.refreshForCurrentDay()
            runCurrent()
            assertEquals(count + 1, repository.observedDates.size)
        }

    @Test
    fun `history emission after date rollback refreshes the day`() = runTest(dispatcher) {
        val clock = MutableClock(Instant.parse("2026-02-01T12:00:00Z"))
        val originalDay = LocalDate.now(clock)
        val history = FakeHabitHistoryRepository(emptyList())
        val habits = mockk<HabitRepository>()
        val queriedDates = mutableListOf<LocalDate>()
        coEvery { habits.getPendingSchedules(any()) } coAnswers {
            queriedDates += firstArg<LocalDate>()
            emptyList()
        }
        val controller = JiveConditionController(HabitCompletionEvents())
        val published = mutableListOf<JiveCondition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.condition.collect { published += it }
        }
        val viewModel = createViewModel(
            history,
            controller,
            clock,
            EnsureHabitSchedulesUseCase(habits, clock)
        )
        runCurrent()
        assertEquals(listOf(originalDay), history.observedDates)
        queriedDates.clear()

        clock.currentInstant = clock.currentInstant.minusSeconds(86_400)
        // The old Room subscription can emit before the lifecycle monitor notices the change.
        history.replaceHistory(listOf(DailyHabitPerformance(originalDay, 1, 1, true)))
        runCurrent()

        val currentDay = LocalDate.now(clock)
        assertEquals(listOf(originalDay, currentDay), history.observedDates)
        assertEquals(listOf(JiveCondition()), published)
        assertFalse(viewModel.refreshFailed.value)
        assertEquals(setOf(currentDay), queriedDates.toSet())
    }

    private fun createViewModel(
        repository: HabitHistoryRepository,
        controller: JiveConditionController,
        clock: Clock = FixedClock,
        ensureHabitSchedules: EnsureHabitSchedulesUseCase = mockk(relaxed = true)
    ) = JiveConditionViewModel(
        observeJiveCondition = ObserveJiveConditionUseCase(
            repository,
            JiveConditionScorer(),
            ensureHabitSchedules
        ),
        controller = controller,
        clock = clock
    )

    private fun inactiveHistory(dayCount: Int): List<DailyHabitPerformance> =
        List(dayCount) { day ->
            DailyHabitPerformance(
                date = StartDate.plusDays(day.toLong()),
                plannedHabitCount = 4,
                completedHabitCount = 0,
                activityRecorded = false
            )
        }

    private class FakeHabitHistoryRepository(initialHistory: List<DailyHabitPerformance>) :
        HabitHistoryRepository {
        private val history = MutableStateFlow(initialHistory)
        val observedDates = mutableListOf<LocalDate>()

        override fun observeDailyPerformance(
            throughDate: LocalDate
        ): Flow<List<DailyHabitPerformance>> {
            observedDates += throughDate
            return history.map { rows -> rows.filter { it.date <= throughDate } }
        }

        fun replaceHistory(value: List<DailyHabitPerformance>) {
            history.value = value
        }

        override suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>) = Unit

        override fun observeCompletedDates(
            habitId: String,
            fromDate: LocalDate,
            throughDate: LocalDate
        ): Flow<List<LocalDate>> = error("Completion dates are unused by the condition observer")

        override suspend fun setHabitCompleted(
            habitId: String,
            date: LocalDate,
            completed: Boolean
        ) = Unit

        override suspend fun recordDailyCheckIn(date: LocalDate) = Unit
    }

    private class MutableClock(var currentInstant: Instant, var zoneId: ZoneId = ZoneOffset.UTC) :
        Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

        override fun instant(): Instant = currentInstant
    }

    private companion object {
        val StartDate: LocalDate = LocalDate.of(2026, 1, 1)
        val FixedClock: Clock = Clock.fixed(
            Instant.parse("2026-01-31T12:00:00Z"),
            ZoneOffset.UTC
        )
    }
}
