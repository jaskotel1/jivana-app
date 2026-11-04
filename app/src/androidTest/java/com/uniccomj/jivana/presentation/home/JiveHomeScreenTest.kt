package com.uniccomj.jivana.presentation.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JiveHomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeDisplaysCenteredCanvasInTheExistingSquareContainer() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            JivanaTheme {
                Surface(Modifier.fillMaxSize()) { HomeScreen(condition = JiveCondition()) }
            }
        }
        compose.mainClock.advanceTimeBy(900)
        val character = compose.onNodeWithTag("home_jive").assertIsDisplayed()
        val bounds = character.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(abs(bounds.width - bounds.height) < 1f)
        assertTrue(abs(bounds.center.x - root.center.x) < 1f)
        assertTrue(abs(bounds.width - root.width * 0.72f) < 1f)
        assertTrue(bounds.top >= root.top && bounds.bottom <= root.bottom)
        saveBitmap("home_canvas.png", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun conditionChangesUpdateTheCharacterAndAnInactiveScreenFreezesTheTransition() {
        compose.mainClock.autoAdvance = false
        val condition = mutableStateOf(JiveCondition())
        val owner = ownerAt(Lifecycle.State.RESUMED)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                JivanaTheme { HomeScreen(condition = condition.value) }
            }
        }
        compose.mainClock.advanceTimeBy(900)
        val initial = characterBitmap()
        val extremes = listOf(
            JiveCondition(
                mood = JiveMood.DEVASTATED,
                energy = JiveEnergy.EXHAUSTED,
                sleepiness = JiveSleepiness.SLEEPING
            ),
            JiveCondition(mood = JiveMood.ECSTATIC)
        )
        for (next in extremes) {
            compose.runOnUiThread { condition.value = next }
            compose.mainClock.advanceTimeBy(500)
            assertFalse(initial.sameAs(characterBitmap()))
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
            val paused = characterBitmap()
            compose.mainClock.advanceTimeBy(1000)
            assertTrue(paused.sameAs(characterBitmap()))
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        }
    }

    @Test
    fun homeIdleStopsWhileInactiveAndResumesFromItsPausedTime() {
        compose.mainClock.autoAdvance = false
        val owner = ownerAt(Lifecycle.State.RESUMED)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                JivanaTheme { HomeScreen(condition = JiveCondition()) }
            }
        }
        compose.mainClock.advanceTimeBy(900)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        val paused = characterBitmap()
        compose.mainClock.advanceTimeBy(3000)
        assertTrue(paused.sameAs(characterBitmap()))
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(800)
        assertFalse(paused.sameAs(characterBitmap()))
    }

    private fun ownerAt(state: Lifecycle.State): HomeLifecycleOwner {
        lateinit var owner: HomeLifecycleOwner
        compose.runOnUiThread {
            owner = HomeLifecycleOwner()
            owner.registry.currentState = state
        }
        return owner
    }

    private fun characterBitmap(): Bitmap = compose.onNodeWithTag("home_jive")
        .captureToImage().asAndroidBitmap()

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class HomeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
