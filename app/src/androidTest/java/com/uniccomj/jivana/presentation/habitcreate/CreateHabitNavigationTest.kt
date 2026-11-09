package com.uniccomj.jivana.presentation.habitcreate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.MainActivity
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.local.database.JivanaMigrations
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateHabitNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeOpensFormRestoresAfterActivityRecreationAndSavesToRoom() {
        compose.mainClock.autoAdvance = false
        val name = "UI habit ${System.nanoTime()}"
        compose.onNodeWithTag("habit_create_open").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("habit_name").performTextInput(name)
        compose.onNodeWithTag(
            "habit_description"
        ).performTextInput("Description survives recreation")
        compose.onNodeWithTag("habit_weekly").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("habit_day_SUNDAY").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()

        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("habit_name").assertTextContains(name)
        compose.onNodeWithTag(
            "habit_description"
        ).assertTextContains("Description survives recreation")
        compose.onNodeWithTag("habit_day_SUNDAY").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("habit_save").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodes(
                androidx.compose.ui.test.hasTestTag("habit_create_open")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("habit_create_open").assertIsDisplayed()
        verifyPersistedHabit(name)
    }

    private fun verifyPersistedHabit(name: String) {
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JivanaDatabase::class.java,
            "jivana.db"
        ).addMigrations(JivanaMigrations.Migration1To2).build()
        try {
            database.openHelper.readableDatabase.query(
                "SELECT id, description, scheduledDaysMask, startEpochDay FROM habits WHERE name = ?",
                arrayOf(name)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Description survives recreation", cursor.getString(1))
                assertEquals(63, cursor.getInt(2)) // Monday through Saturday.
                val date = LocalDate.ofEpochDay(cursor.getLong(3))
                assertEquals(LocalDate.now(), date)
                val habitId = cursor.getString(0)
                database.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM habit_schedules WHERE habitId = ?",
                    arrayOf(habitId)
                ).use { plan ->
                    plan.moveToFirst()
                    assertEquals(if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 1, plan.getInt(0))
                }
                assertEquals(1, cursor.count)
            }
        } finally {
            database.close()
        }
    }
}
