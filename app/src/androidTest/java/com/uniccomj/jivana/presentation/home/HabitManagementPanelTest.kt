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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.Habit
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
                    }
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
                        onResolveConflict = { _, _ -> }
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
    }

    private fun Context.inLanguage(language: String): Context = createConfigurationContext(
        Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
    )
}
