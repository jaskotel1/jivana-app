package com.uniccomj.jivana.data.repository

import com.uniccomj.jivana.data.local.dao.HabitHistoryDao
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitCompletionEventsTest {
    @Test
    fun `only successful new completions notify listeners`() = runTest {
        val dao = mockk<HabitHistoryDao>()
        val events = HabitCompletionEvents()
        val repository = RoomHabitHistoryRepository(dao, JiveScoringConfig(), events)
        val date = LocalDate.of(2026, 11, 5)
        var completions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.completions.collect { completions++ }
        }
        coEvery { dao.setHabitCompleted("habit", date.toEpochDay(), true) } returnsMany
            listOf(true, false)
        coEvery { dao.setHabitCompleted("habit", date.toEpochDay(), false) } returns false

        repository.setHabitCompleted("habit", date, true)
        repository.setHabitCompleted("habit", date, true)
        repository.setHabitCompleted("habit", date, false)
        assertEquals(1, completions)

        coEvery { dao.setHabitCompleted("habit", date.toEpochDay(), true) } throws
            IOException("Write failed")
        var failed = false
        try {
            repository.setHabitCompleted("habit", date, true)
        } catch (_: IOException) {
            failed = true
        }
        assertEquals(true, failed)
        assertEquals(1, completions)
    }
}
