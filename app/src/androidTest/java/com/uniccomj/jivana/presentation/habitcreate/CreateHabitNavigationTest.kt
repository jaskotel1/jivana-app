package com.uniccomj.jivana.presentation.habitcreate

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.MainActivity
import com.uniccomj.jivana.R
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.local.database.JivanaMigrations
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateHabitNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeOpensCatalogRestoresEditedTemplateAfterActivityRecreationAndSavesToRoom() {
        compose.mainClock.autoAdvance = false
        val name = "UI habit ${System.nanoTime()}"
        waitForTag("habit_create_open")
        click("habit_create_open")
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText(text(R.string.habit_catalog_title)).assertIsDisplayed()
        click("habit_category_activity")
        compose.mainClock.advanceTimeByFrame()
        click("habit_template_workout")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("habit_name")
            .assertTextContains(text(R.string.habit_template_workout))
        compose.onNodeWithTag("habit_description").assertTextContains(
            text(R.string.habit_template_workout_description)
        )
        compose.onNodeWithTag("habit_target_3").assertIsSelected()
        compose.onNodeWithTag("habit_name").performTextReplacement(name)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(
            "habit_description"
        ).performTextReplacement("Description survives recreation")
        compose.mainClock.advanceTimeByFrame()
        click("habit_target_5")
        compose.mainClock.advanceTimeByFrame()
        val start = LocalDate.now()
        click("habit_start_date")
        compose.mainClock.advanceTimeBy(500)
        click("habit_date_confirm")
        compose.mainClock.advanceTimeBy(500)

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        click("habit_template_workout")
        compose.mainClock.advanceTimeByFrame()

        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("habit_name").assertTextContains(name)
        compose.onNodeWithTag(
            "habit_description"
        ).assertTextContains("Description survives recreation")
        compose.onNodeWithTag("habit_target_5").assertIsSelected()
        val formattedStart = start.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(compose.activity.resources.configuration.locales[0])
        )
        compose.onNodeWithTag("habit_start_date")
            .assertTextContains(formattedStart)
        click("habit_save")
        compose.mainClock.advanceTimeBy(700)
        waitForTag("habit_active_name")
        compose.onNodeWithTag("habit_active_name").assertTextContains(name)
        compose.onNodeWithTag("habit_create_open").assertDoesNotExist()
        compose.onNodeWithText(
            text(R.string.habit_active_limit)
        ).assertIsDisplayed()
        verifyPersistedHabit(name, start, archived = false)
        compose.onNodeWithTag(
            "habit_cycle_progress"
        ).assertTextContains(text(R.string.habit_cycle_day, 1, 60))
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(700)
        waitForTag("habit_cycle_progress")
        compose.onNodeWithTag(
            "habit_cycle_progress"
        ).assertTextContains(text(R.string.habit_cycle_day, 1, 60))

        compose.mainClock.advanceTimeBy(700)
        click("habit_archive_open")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(
            text(R.string.habit_archive_confirmation, name),
            useUnmergedTree = true
        ).assertIsDisplayed()
        verifyPersistedHabit(name, start, archived = false)
        click("habit_manage_confirm")
        waitForTag("habit_create_open")
        compose.onNodeWithTag("habit_create_open").assertIsDisplayed()
        verifyPersistedHabit(name, start, archived = true)
    }

    // Navigation uses a paused clock to control Jive's continuous Home animation.
    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun verifyPersistedHabit(name: String, start: LocalDate, archived: Boolean) {
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JivanaDatabase::class.java,
            "jivana.db"
        ).addMigrations(
            JivanaMigrations.Migration1To2,
            JivanaMigrations.Migration2To3,
            JivanaMigrations.Migration3To4,
            JivanaMigrations.Migration4To5
        ).build()
        try {
            database.openHelper.readableDatabase.query(
                """
                SELECT id, description, scheduledDaysMask, startEpochDay, archivedAtEpochMillis,
                    scheduleType, weeklyTarget
                FROM habits WHERE name = ?
                """.trimIndent(),
                arrayOf(name)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Description survives recreation", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(start, LocalDate.ofEpochDay(cursor.getLong(3)))
                assertEquals(!archived, cursor.isNull(4))
                assertEquals("WEEKLY_TARGET", cursor.getString(5))
                assertEquals(5, cursor.getInt(6))
                val habitId = cursor.getString(0)
                database.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM habit_schedules WHERE habitId = ?",
                    arrayOf(habitId)
                ).use { plan ->
                    plan.moveToFirst()
                    assertEquals(0, plan.getInt(0))
                }
                assertEquals(1, cursor.count)
            }
        } finally {
            database.close()
        }
    }
}
