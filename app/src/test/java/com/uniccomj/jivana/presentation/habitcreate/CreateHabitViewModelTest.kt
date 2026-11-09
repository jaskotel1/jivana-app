package com.uniccomj.jivana.presentation.habitcreate

import androidx.lifecycle.SavedStateHandle
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.HabitValidationError
import com.uniccomj.jivana.domain.usecase.HabitValidationException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateHabitViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val create = mockk<CreateHabitUseCase>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid save clicks and edits while saving produce one request`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Habit>()
        coEvery { create(any(), any(), any(), any()) } coAnswers { gate.await() }
        val vm = CreateHabitViewModel(create, SavedStateHandle())
        vm.updateName("Water")
        vm.save()
        vm.save()
        vm.updateName("Other")
        runCurrent()
        assertTrue(vm.state.value.saving)
        assertEquals("Water", vm.state.value.name)
        gate.complete(habit())
        advanceUntilIdle()
        vm.save()
        assertTrue(vm.state.value.saved)
        coVerify(exactly = 1) { create("Water", "", any(), any()) }
    }

    @Test
    fun `write errors preserve the form and allow retry with the same token`() =
        runTest(dispatcher) {
            val tokens = mutableListOf<String>()
            coEvery { create(any(), any(), any(), capture(tokens)) } throws IOException("Disk full")
            val vm = CreateHabitViewModel(create, SavedStateHandle())
            vm.updateName("Water")
            vm.updateDescription("Notes")
            vm.selectEveryDay(false)
            vm.toggleDay(DayOfWeek.SUNDAY)
            vm.save()
            advanceUntilIdle()
            assertTrue(vm.state.value.saveFailed)
            assertFalse(vm.state.value.saving)
            assertEquals("Notes", vm.state.value.description)
            assertFalse(DayOfWeek.SUNDAY in vm.state.value.scheduledDays)
            coEvery { create(any(), any(), any(), capture(tokens)) } returns habit()
            vm.save()
            advanceUntilIdle()
            assertEquals(tokens.first(), tokens.last())
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `saved handle restores text selected days frequency and request identity`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = CreateHabitViewModel(create, handle)
            vm.updateName("Read")
            vm.updateDescription("Ten pages")
            vm.selectEveryDay(false)
            vm.toggleDay(DayOfWeek.MONDAY)
            val restoredHandle =
                SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val restored = CreateHabitViewModel(create, restoredHandle)
            assertEquals(vm.state.value, restored.state.value)
            assertEquals(
                handle.get<String>("habit.request"),
                restoredHandle.get<String>("habit.request")
            )
        }

    @Test
    fun `validation errors are shown and do not clear input`() = runTest(dispatcher) {
        coEvery { create(any(), any(), any(), any()) } throws
            HabitValidationException(HabitValidationError.NAME_REQUIRED)
        val vm = CreateHabitViewModel(create, SavedStateHandle())
        vm.updateName("   ")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.nameRequired)
        vm.updateName("Water")
        assertFalse(vm.state.value.nameRequired)
        coEvery { create(any(), any(), any(), any()) } throws
            HabitValidationException(HabitValidationError.DAYS_REQUIRED)
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.daysRequired)
    }

    private fun habit() = Habit("id", "Water", null, HabitSchedule.Daily, LocalDate.of(2026, 11, 5))
}
