package com.uniccomj.jivana.presentation.habitcreate

import androidx.lifecycle.SavedStateHandle
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.repository.HabitOperationException
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
        coEvery {
            create.createWithSchedule(any(), any(), any(), any(), null)
        } coAnswers { gate.await() }
        val vm = CreateHabitViewModel(create, SavedStateHandle()).apply { openForm() }
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
        coVerify(exactly = 1) {
            create.createWithSchedule("Water", "", HabitSchedule.Daily, any(), null)
        }
    }

    @Test
    fun `write errors preserve the form and allow retry with the same token`() =
        runTest(dispatcher) {
            val tokens = mutableListOf<String>()
            coEvery {
                create.createWithSchedule(any(), any(), any(), capture(tokens), null)
            } throws IOException("Disk full")
            val vm = CreateHabitViewModel(create, SavedStateHandle()).apply { openForm() }
            vm.updateName("Water")
            vm.updateDescription("Notes")
            vm.selectWeekly(true)
            vm.selectWeeklyTarget(7)
            vm.save()
            advanceUntilIdle()
            assertEquals(HabitSaveFailure.STORAGE, vm.state.value.saveFailure)
            assertFalse(vm.state.value.saving)
            assertEquals("Notes", vm.state.value.description)
            assertEquals(7, vm.state.value.weeklyTarget)
            coEvery {
                create.createWithSchedule(
                    "Water",
                    "Notes",
                    HabitSchedule.WeeklyTarget(7),
                    capture(tokens),
                    null
                )
            } returns habit()
            vm.save()
            advanceUntilIdle()
            assertEquals(tokens.first(), tokens.last())
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `habit limit and token conflicts show distinct errors`() = runTest(dispatcher) {
        val vm = CreateHabitViewModel(create, SavedStateHandle()).apply { openForm() }
        vm.updateName("Water")
        coEvery { create.createWithSchedule(any(), any(), any(), any(), null) } throws
            HabitOperationException.ActiveHabitLimitReached()
        vm.save()
        advanceUntilIdle()
        assertEquals(HabitSaveFailure.ACTIVE_HABIT, vm.state.value.saveFailure)
        coEvery { create.createWithSchedule(any(), any(), any(), any(), null) } throws
            HabitOperationException.RequestTokenConflict()
        vm.save()
        advanceUntilIdle()
        assertEquals(HabitSaveFailure.REQUEST_TOKEN, vm.state.value.saveFailure)
        assertEquals("Water", vm.state.value.name)
    }

    @Test
    fun `saved handle restores draft frequency target date and request identity`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = CreateHabitViewModel(create, handle).apply { openForm() }
            vm.updateName("Read")
            vm.updateDescription("Ten pages")
            vm.selectWeekly(true)
            vm.selectWeeklyTarget(2)
            vm.updateStartDate(LocalDate.of(2026, 11, 12))
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
        coEvery { create.createWithSchedule(any(), any(), any(), any(), null) } throws
            HabitValidationException(HabitValidationError.NAME_REQUIRED)
        val vm = CreateHabitViewModel(create, SavedStateHandle()).apply { openForm() }
        vm.updateName("   ")
        vm.save()
        advanceUntilIdle()
        assertEquals(HabitValidationError.NAME_REQUIRED, vm.state.value.validationError)
        vm.updateName("Water")
        assertEquals(null, vm.state.value.validationError)
        coEvery { create.createWithSchedule(any(), any(), any(), any(), null) } throws
            HabitValidationException(HabitValidationError.NAME_TOO_LONG)
        vm.save()
        advanceUntilIdle()
        assertEquals(HabitValidationError.NAME_TOO_LONG, vm.state.value.validationError)
    }

    @Test
    fun `selecting and reselecting a template preserves edits and never saves by itself`() =
        runTest(dispatcher) {
            val vm = CreateHabitViewModel(create, SavedStateHandle())
            val template = HabitCatalog.flatMap { it.templates }.first { it.id == "workout" }
            vm.openForm(template, "Work out", "Exercise")
            assertEquals("Work out", vm.state.value.name)
            assertEquals("Exercise", vm.state.value.description)
            assertTrue(vm.state.value.weekly)
            assertEquals(3, vm.state.value.weeklyTarget)
            vm.updateName("My training")
            vm.updateDescription("My notes")
            vm.selectWeeklyTarget(6)
            vm.selectWeekly(false)
            vm.selectWeekly(true)
            vm.selectWeeklyTarget(0)
            vm.selectWeeklyTarget(8)
            vm.returnToCatalog()
            vm.openForm(template, "Trening", "Ćwiczenia")
            assertEquals("My training", vm.state.value.name)
            assertEquals("My notes", vm.state.value.description)
            assertEquals(6, vm.state.value.weeklyTarget)
            coVerify(exactly = 0) { create.createWithSchedule(any(), any(), any(), any(), null) }
        }

    private fun habit() = Habit("id", "Water", null, HabitSchedule.Daily, LocalDate.of(2026, 11, 5))
}
