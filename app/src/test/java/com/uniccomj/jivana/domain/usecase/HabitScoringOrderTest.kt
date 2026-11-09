package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.repository.HabitHistoryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HabitScoringOrderTest {
    @Test
    fun `history is not read for scoring until missing schedules have committed`() = runTest {
        val history = mockk<HabitHistoryRepository>()
        val ensure = mockk<EnsureHabitSchedulesUseCase>()
        val date = LocalDate.of(2026, 11, 5)
        val operations = mutableListOf<String>()
        coEvery { ensure(date) } coAnswers {
            operations += "schedules committed"
            false
        }
        every { history.observeDailyPerformance(date) } answers {
            assertEquals(listOf("schedules committed"), operations)
            operations += "observe history"
            flowOf(emptyList())
        }
        assertEquals(
            JiveCondition(),
            ObserveJiveConditionUseCase(history, JiveConditionScorer(), ensure)(date).first()
        )
        assertEquals(
            listOf("schedules committed", "observe history", "schedules committed"),
            operations
        )
    }

    @Test
    fun `late creation snapshot is reloaded after catching up before scoring`() = runTest {
        val history = mockk<HabitHistoryRepository>()
        val ensure = mockk<EnsureHabitSchedulesUseCase>()
        val date = LocalDate.of(2026, 11, 5)
        coEvery { ensure(date) } returnsMany listOf(false, true)
        every { history.observeDailyPerformance(date) } returnsMany listOf(
            flowOf(emptyList()),
            flowOf(listOf(DailyHabitPerformance(date, 1, 1, true)))
        )
        val condition = ObserveJiveConditionUseCase(
            history,
            JiveConditionScorer(),
            ensure
        )(date).first()
        assertEquals(JiveMood.ECSTATIC, condition.mood)
    }

    @Test
    fun `failed schedule catch up does not score incomplete history`() = runTest {
        val history = mockk<HabitHistoryRepository>()
        val ensure = mockk<EnsureHabitSchedulesUseCase>()
        val date = LocalDate.of(2026, 11, 5)
        coEvery { ensure(date) } throws IOException("Failed to commit schedules")
        try {
            ObserveJiveConditionUseCase(history, JiveConditionScorer(), ensure)(date).first()
            error("Expected failure")
        } catch (_: IOException) {
            verify(exactly = 0) { history.observeDailyPerformance(any()) }
        }
    }
}
