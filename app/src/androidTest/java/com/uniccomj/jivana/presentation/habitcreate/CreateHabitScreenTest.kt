package com.uniccomj.jivana.presentation.habitcreate

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.R
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.model.HabitScheduleProgress
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.repository.HabitRepository
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateHabitScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val repository = FormRepository()
    private var returned = 0

    @Test
    fun nameIsRequiredAndDailyIgnoresTheLastWeeklyTarget() {
        showForm()
        click("habit_save")
        compose.onNodeWithText(text(R.string.habit_name_required))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("habit_name").performTextInput("Read")
        compose.onNodeWithTag("habit_description").performTextInput("Ten pages")
        click("habit_weekly")
        click("habit_target_7")
        click("habit_daily")
        compose.onNodeWithTag("habit_target_7").assertDoesNotExist()
        click("habit_weekly")
        compose.onNodeWithTag("habit_target_7").assertIsSelected()
        click("habit_daily")
        click("habit_save")
        compose.waitForIdle()
        assertEquals(1, returned)
        val habit = repository.created.values.single()
        assertEquals("Read", habit.name)
        assertEquals("Ten pages", habit.description)
        assertEquals(HabitSchedule.Daily, habit.schedule)
    }

    @Test
    fun failedSavePreservesInputAndPendingRetryDisablesDuplicateSubmission() {
        showForm()
        compose.onNodeWithTag("habit_name").performTextInput("Water")
        repository.failure = IOException("Write failed")
        click("habit_save")
        compose.onNodeWithTag("habit_save_error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("habit_name").assertTextContains("Water")
        repository.failure = null
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        click("habit_save")
        compose.onNodeWithTag("habit_save").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("habit_cancel").assertIsNotEnabled()
        assertEquals(0, repository.created.size)
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        assertEquals(1, returned)
        assertEquals(1, repository.created.size)
        assertEquals(2, repository.attempts)
    }

    @Test
    fun returningToCatalogPreservesTheDraftUntilItIsCancelled() {
        showForm()
        compose.onNodeWithTag("habit_name").performTextInput("Unfinished")
        compose.onNodeWithTag("habit_cancel").performScrollTo().assertIsEnabled().performClick()
        click("habit_catalog_custom")
        compose.onNodeWithTag("habit_name").assertTextContains("Unfinished")
        click("habit_cancel")
        click("habit_catalog_cancel")
        assertEquals(1, returned)
        assertEquals(0, repository.created.size)
    }

    @Test
    fun activeHabitRejectionIsVisibleForTemplatesAndCustomHabits() {
        repository.failure = HabitOperationException.ActiveHabitLimitReached()
        showCatalog()
        click("habit_category_health")
        click("habit_template_drink_water")
        click("habit_save")
        val error = compose.onNodeWithTag("habit_save_error")
        error.performScrollTo().assertTextContains(text(R.string.habit_active_limit))

        click("habit_cancel")
        click("habit_catalog_custom")
        compose.onNodeWithTag("habit_name").performTextInput("Custom habit")
        click("habit_save")
        error.performScrollTo().assertTextContains(text(R.string.habit_active_limit))
        assertEquals(2, repository.attempts)
        assertEquals(0, repository.created.size)
    }

    private fun showForm() {
        showCatalog()
        click("habit_catalog_custom")
    }

    private fun showCatalog() {
        val vm =
            CreateHabitViewModel(
                CreateHabitUseCase(repository, Clock.systemDefaultZone()),
                SavedStateHandle()
            )
        compose.setContent { JivanaTheme { CreateHabitRoute(vm) { returned++ } } }
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private class FormRepository : HabitRepository {
        val created = mutableMapOf<String, Habit>()
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var attempts = 0

        override suspend fun create(habit: Habit, initialDates: Set<LocalDate>): Habit {
            attempts++
            failure?.let { throw it }
            gate?.await()
            created[habit.id] = habit
            return habit
        }

        override suspend fun getById(id: String): Habit? = created[id]

        override fun observeActiveHabits(): Flow<List<Habit>> = error("Not exercised here")

        override suspend fun archive(id: String, archivedAt: ZonedDateTime): Habit =
            error("Not exercised here")

        override suspend fun resolveActiveHabitConflict(
            keepId: String,
            expectedActiveIds: Set<String>,
            archivedAt: ZonedDateTime
        ): Unit = error("Not exercised here")
        override suspend fun getPendingSchedules(
            throughDate: LocalDate
        ): List<HabitScheduleProgress> = emptyList()
        override suspend fun advanceSchedule(
            progress: HabitScheduleProgress,
            throughDate: LocalDate,
            dates: Set<LocalDate>
        ): Boolean = error("Not used by the form")
    }
}
