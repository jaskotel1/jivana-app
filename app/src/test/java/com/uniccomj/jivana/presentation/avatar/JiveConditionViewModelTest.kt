package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import com.uniccomj.jivana.domain.usecase.JiveConditionScorer
import com.uniccomj.jivana.domain.usecase.ObserveJiveConditionUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

        val firstController = JiveConditionController()
        createViewModel(repository, firstController)
        advanceUntilIdle()

        val recreatedController = JiveConditionController()
        createViewModel(repository, recreatedController)
        advanceUntilIdle()

        assertEquals(firstController.condition.value, recreatedController.condition.value)
        assertEquals(JiveSleepiness.SLEEPING, recreatedController.condition.value.sleepiness)
    }

    @Test
    fun `history change is automatically rescored and published`() = runTest(dispatcher) {
        val repository = FakeHabitHistoryRepository(emptyList())
        val controller = JiveConditionController()
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
            controller = JiveConditionController(),
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

    private fun createViewModel(
        repository: HabitHistoryRepository,
        controller: JiveConditionController,
        clock: Clock = FixedClock
    ) = JiveConditionViewModel(
        observeJiveCondition = ObserveJiveConditionUseCase(repository, JiveConditionScorer()),
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
            return history
        }

        fun replaceHistory(value: List<DailyHabitPerformance>) {
            history.value = value
        }

        override suspend fun scheduleHabit(habitId: String, dates: Set<LocalDate>) = Unit

        override suspend fun setHabitCompleted(
            habitId: String,
            date: LocalDate,
            completed: Boolean
        ) = Unit

        override suspend fun recordDailyCheckIn(date: LocalDate) = Unit
    }

    private class MutableClock(
        var currentInstant: Instant,
        private val zoneId: ZoneId = ZoneOffset.UTC
    ) : Clock() {
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
