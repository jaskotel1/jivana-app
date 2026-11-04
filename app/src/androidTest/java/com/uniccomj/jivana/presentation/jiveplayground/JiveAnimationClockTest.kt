package com.uniccomj.jivana.presentation.jiveplayground

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness
import com.uniccomj.jivana.presentation.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class JiveAnimationClockTest {
    private val durationScale = TestDurationScale()

    @get:Rule
    val compose = createComposeRule(effectContext = durationScale)

    @Test
    fun zeroScaleShowsCurrentConditionAndSupportsBothLiveScaleChanges() {
        compose.mainClock.autoAdvance = false
        val condition = mutableStateOf(JiveCondition())
        compose.runOnUiThread { durationScale.scaleFactor = 0f }
        compose.setContent { JivanaTheme { HomeScreen(condition.value) } }
        compose.mainClock.advanceTimeBy(100)
        val initial = bitmap()
        compose.runOnUiThread { condition.value = JiveCondition(mood = JiveMood.ECSTATIC) }
        compose.mainClock.advanceTimeBy(100)
        val happy = bitmap()
        assertFalse(initial.sameAs(happy))
        compose.mainClock.advanceTimeBy(1000)
        assertTrue(happy.sameAs(bitmap()))

        compose.runOnUiThread { durationScale.scaleFactor = 1f }
        compose.mainClock.advanceTimeBy(900)
        assertFalse(happy.sameAs(bitmap()))
        compose.runOnUiThread { durationScale.scaleFactor = 0f }
        compose.mainClock.advanceTimeBy(100)
        val stopped = bitmap()
        compose.mainClock.advanceTimeBy(1000)
        assertTrue(stopped.sameAs(bitmap()))
        compose.runOnUiThread {
            condition.value =
                JiveCondition(JiveMood.DEVASTATED, sleepiness = JiveSleepiness.SLEEPING)
        }
        compose.mainClock.advanceTimeBy(100)
        assertFalse(stopped.sameAs(bitmap()))
    }

    @Test
    fun lifecyclePauseKeepsPhasesAndResumeDoesNotCatchUpOrDuplicateTheClock() {
        compose.mainClock.autoAdvance = false
        val animator = JiveAnimator()
        lateinit var owner: ClockLifecycleOwner
        compose.runOnUiThread {
            owner = ClockLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                JiveAnimationClock(animator) {}
            }
        }
        compose.mainClock.advanceTimeBy(500)
        repeat(2) {
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
            val paused = animator.motionSeconds
            val blink = animator.blinkSeconds
            compose.mainClock.advanceTimeBy(5000)
            assertEquals(paused, animator.motionSeconds)
            assertEquals(blink, animator.blinkSeconds)
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
            compose.mainClock.advanceTimeBy(160)
            assertTrue(animator.motionSeconds > paused)
            assertTrue(animator.motionSeconds - paused < 0.2f)
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.DESTROYED }
        val stopped = animator.motionSeconds
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(stopped, animator.motionSeconds)
    }

    private fun bitmap() = compose.onNodeWithTag("home_jive").captureToImage().asAndroidBitmap()

    private class TestDurationScale : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }

    private class ClockLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
