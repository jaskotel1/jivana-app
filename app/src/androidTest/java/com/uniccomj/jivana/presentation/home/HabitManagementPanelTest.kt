package com.uniccomj.jivana.presentation.home

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitCycle
import com.uniccomj.jivana.domain.model.HabitCyclePause
import com.uniccomj.jivana.domain.model.HabitSchedule
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HabitManagementPanelTest {
    @get:Rule
    val compose = createComposeRule()
    private val first =
        Habit("water", "Water", null, HabitSchedule.Daily, LocalDate.of(2026, 11, 1))
    private val second = first.copy(id = "read", name = "Read")

    @Test
    fun legacyConflictRequiresAChoiceAndConfirmationBeforeKeepingOneHabit() {
        var activeHabits by mutableStateOf(listOf(first, second))
        var resolved: Pair<String, Set<String>>? = null
        compose.setContent {
            JivanaTheme {
                HabitManagementPanel(
                    state = HabitManagementState(activeHabits = activeHabits),
                    onCreateHabit = { error("Cannot create during a conflict") },
                    onArchive = { error("Must resolve the whole conflict") },
                    onResolveConflict = { keep, expected ->
                        resolved = keep to expected
                        activeHabits = activeHabits.filter { it.id == keep }
                    },
                    onStartCycle = { error("Cycle start is a separate action") },
                    onPause = {},
                    onResume = { _, _ -> }
                )
            }
        }

        compose.onNodeWithTag("habit_conflict").assertIsDisplayed()
        compose.onNodeWithTag("habit_create_open").assertDoesNotExist()
        compose.onNodeWithTag("habit_keep_water").assertIsDisplayed()
        compose.onNodeWithTag("habit_keep_read").performClick()
        compose.runOnIdle { assertNull(resolved) }
        compose.onNodeWithTag("habit_manage_confirm").performClick()
        compose.onNodeWithTag("habit_active_name").assertTextContains("Read")
        compose.onNodeWithTag("habit_conflict").assertDoesNotExist()
        compose.onNodeWithTag("habit_create_open").assertDoesNotExist()
        compose.runOnIdle { assertEquals("read" to setOf("water", "read"), resolved) }
    }

    @Test
    fun habitLimitExplainsWhatToDoInEnglishAndPolish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var localized by mutableStateOf(context.inLanguage("en"))
        var startedHabit: String? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides localized.resources.configuration,
                LocalContext provides localized
            ) {
                JivanaTheme {
                    HabitManagementPanel(
                        state = HabitManagementState(activeHabits = listOf(first)),
                        onCreateHabit = {},
                        onArchive = {},
                        onResolveConflict = { _, _ -> },
                        onStartCycle = { startedHabit = it },
                        onPause = {},
                        onResume = { _, _ -> }
                    )
                }
            }
        }
        compose.onNodeWithText("You can have one active habit. Archive it to create another.")
            .assertIsDisplayed()
        compose.runOnIdle { localized = context.inLanguage("pl") }
        compose.onNodeWithText(
            "Możesz mieć jeden aktywny nawyk. Zarchiwizuj go, aby utworzyć następny."
        )
            .assertIsDisplayed()
        compose.onNodeWithText("Rozpocznij cykl 60-dniowy").performClick()
        compose.runOnIdle { assertEquals(first.id, startedHabit) }
    }

    @Test
    fun pauseControlsShowLocalizedProgressAndDisableNewPausesAtTheLimit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val today = first.startDate.plusDays(3)
        val cycle = HabitCycle("cycle", first.id, first.startDate)
        val paused = cycle.copy(pauses = listOf(HabitCyclePause(today)))
        var progress by mutableStateOf(cycle.progressOn(today).copy(remainingPauses = 1))
        var localized by mutableStateOf(context.inLanguage("en"))
        var resumeRequested: Pair<String, LocalDate>? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides localized.resources.configuration,
                LocalContext provides localized
            ) {
                JivanaTheme {
                    HabitManagementPanel(
                        state = HabitManagementState(listOf(first), cycleProgress = progress),
                        onCreateHabit = {},
                        onArchive = {},
                        onResolveConflict = { _, _ -> },
                        onStartCycle = {},
                        onPause = { progress = paused.progressOn(today.plusDays(1)) },
                        onResume = { id, start ->
                            resumeRequested = id to start
                            progress = cycle.progressOn(today)
                        }
                    )
                }
            }
        }
        compose.onNodeWithText("Pauses remaining: 1").assertIsDisplayed()
        compose.onNodeWithText("Pause habit").performClick()
        compose.onNodeWithText(
            "Pausing today freezes your 60-day cycle. Your previous habit history is kept.",
            useUnmergedTree = true
        ).assertIsDisplayed()
        compose.onNodeWithTag("habit_pause_confirm").performClick()
        compose.onNodeWithText("Habit paused").assertIsDisplayed()
        compose.onNodeWithText("Days on pause: 2").assertIsDisplayed()
        compose.runOnIdle { localized = context.inLanguage("pl") }
        compose.onNodeWithText("Nawyk wstrzymany").assertIsDisplayed()
        compose.onNodeWithText("Dni pauzy: 2").assertIsDisplayed()
        compose.onNodeWithText("Wznów nawyk").performClick()
        compose.runOnIdle { assertEquals(first.id to today, resumeRequested) }
        compose.onNodeWithText("Pozostałe pauzy: 0").assertIsDisplayed()
        compose.onNodeWithTag("habit_pause_open").assertIsNotEnabled()
    }

    private fun Context.inLanguage(language: String): Context = createConfigurationContext(
        Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
    )
}
