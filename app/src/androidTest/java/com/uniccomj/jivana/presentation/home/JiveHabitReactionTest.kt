package com.uniccomj.jivana.presentation.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.repository.RoomHabitHistoryRepository
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import com.uniccomj.jivana.presentation.avatar.JiveConditionController
import com.uniccomj.jivana.presentation.components.JiveHomeCharacter
import com.uniccomj.jivana.presentation.jiveplayground.JiveVisualState
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JiveHabitReactionTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: JivanaDatabase
    private lateinit var repository: RoomHabitHistoryRepository
    private val events = HabitCompletionEvents()
    private val controller = JiveConditionController(events)
    private val date = LocalDate.of(2026, 11, 5)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JivanaDatabase::class.java
        ).build()
        repository =
            RoomHabitHistoryRepository(database.habitHistoryDao(), JiveScoringConfig(), events)
        runBlocking { repository.scheduleHabit("habit", setOf(date)) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun committedCompletionAnimatesAndReturnsWithoutReplayingOnRecreation() {
        val owner = showCharacter()
        val resting = bitmap()
        completeHabit()
        compose.mainClock.advanceTimeBy(560)
        assertFalse(resting.sameAs(bitmap()))
        compose.mainClock.advanceTimeBy(1400)
        assertTrue(resting.sameAs(bitmap()))

        completeHabit() // The ignored duplicate must not restart the celebration.
        compose.mainClock.advanceTimeBy(560)
        assertTrue(resting.sameAs(bitmap()))
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(560)
        assertTrue(resting.sameAs(bitmap()))
    }

    @Test
    fun hiddenCompletionsAreDroppedAndAnActiveReactionPausesWithTheClock() {
        val owner = showCharacter()
        val resting = bitmap()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        completeHabit()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(560)
        assertTrue(resting.sameAs(bitmap()))

        runBlocking { repository.setHabitCompleted("habit", date, false) }
        completeHabit()
        compose.mainClock.advanceTimeBy(400)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        val paused = bitmap()
        assertFalse(resting.sameAs(paused))
        compose.mainClock.advanceTimeBy(2000)
        assertTrue(paused.sameAs(bitmap()))
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(1400)
        assertTrue(resting.sameAs(bitmap()))
    }

    private fun showCharacter(): ReactionLifecycleOwner {
        compose.mainClock.autoAdvance = false
        lateinit var owner: ReactionLifecycleOwner
        compose.runOnUiThread {
            owner = ReactionLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                JivanaTheme {
                    JiveHomeCharacter(
                        // A still condition lets pixel equality distinguish reactions from idle motion.
                        visualState = JiveVisualState(
                            expression = -1f,
                            motionAmplitude = 0f,
                            eyeOpenness = 0f
                        ),
                        reactions = controller.reactions
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        return owner
    }

    private fun completeHabit() {
        runBlocking { repository.setHabitCompleted("habit", date, true) }
        compose.waitForIdle()
    }

    private fun bitmap() = compose.onNodeWithTag("home_jive").captureToImage().asAndroidBitmap()

    private class ReactionLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
