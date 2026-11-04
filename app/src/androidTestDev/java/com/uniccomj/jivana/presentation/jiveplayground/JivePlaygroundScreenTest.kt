package com.uniccomj.jivana.presentation.jiveplayground

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
import com.uniccomj.jivana.presentation.navigation.AppNavHost
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JivePlaygroundScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyAnimationCanWearEveryOutfit() {
        compose.mainClock.autoAdvance = false
        compose.setContent { JivanaTheme { JivePlaygroundScreen(onBack = {}) } }
        for (animation in JiveAnimation.entries) {
            compose.onNodeWithTag("animation_${animation.name}").performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(550)
            for (outfit in JiveWardrobe.All) {
                compose.onNodeWithTag("outfit_${outfit.id}").performScrollTo().performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("outfit_${outfit.id}").assertIsSelected()
                compose.onNodeWithTag("animation_${animation.name}").assertIsSelected()
                saveCharacter("${animation.name.lowercase()}_${outfit.id}")
            }
        }
    }

    @Test
    fun clothesCanChangeDuringWalkingAndHappyReturnsToIdle() {
        compose.mainClock.autoAdvance = false
        compose.setContent { JivanaTheme { JivePlaygroundScreen(onBack = {}) } }
        compose.onNodeWithTag("animation_WALKING").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(500)
        for (id in listOf("hoodie", "sport", "default")) {
            compose.onNodeWithTag("outfit_$id").performScrollTo().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("outfit_$id").assertIsSelected()
            compose.onNodeWithTag("animation_WALKING").assertIsSelected()
            compose.mainClock.advanceTimeBy(150)
            saveCharacter("walking_$id")
        }
        compose.onNodeWithTag("animation_HAPPY").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(550)
        saveCharacter("happy_default")
        compose.mainClock.advanceTimeBy(1050)
        compose.onNodeWithTag("animation_IDLE").assertIsSelected()
    }

    @Test
    fun animationFreezesWhileInactiveAndResumesWithoutCatchingUp() {
        compose.mainClock.autoAdvance = false
        lateinit var owner: PlaygroundLifecycleOwner
        compose.runOnUiThread {
            owner = PlaygroundLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                JivanaTheme { JivePlaygroundScreen(onBack = {}) }
            }
        }
        compose.onNodeWithTag("animation_WALKING").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(450)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        val frozen = characterBitmap()
        compose.mainClock.advanceTimeBy(3000)
        assertTrue(frozen.sameAs(characterBitmap()))
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(250)
        assertFalse(frozen.sameAs(characterBitmap()))
    }

    @Test
    fun devHomeOpensPlaygroundAndBackReturnsHome() {
        compose.mainClock.autoAdvance = false
        compose.setContent { JivanaTheme { AppNavHost(jiveCondition = JiveCondition()) } }
        compose.onNodeWithTag("jive_playground_open").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("jive_character").assertIsDisplayed()
        compose.onNodeWithTag("playground_back").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("jive_playground_open").assertIsDisplayed()
        compose.onNodeWithTag("jive_character").assertDoesNotExist()
    }

    @Test
    fun conditionPreviewSupportsAllOptionsAndOutfitsAndReturnsToLegacyAnimations() {
        compose.mainClock.autoAdvance = false
        compose.setContent { JivanaTheme { JivePlaygroundScreen(onBack = {}) } }
        selectOption("preview_condition")
        for (mood in JiveMood.entries) {
            selectOption("mood_${mood.name}")
            compose.mainClock.advanceTimeBy(400)
            saveConditionCharacter("condition_${mood.name.lowercase()}")
        }
        for (energy in JiveEnergy.entries) {
            selectOption("energy_${energy.name}")
        }
        for (sleepiness in JiveSleepiness.entries) {
            selectOption("sleepiness_${sleepiness.name}")
        }
        for (outfit in JiveWardrobe.All) {
            selectOption("outfit_${outfit.id}")
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("mood_DEVASTATED").assertIsSelected()
            compose.onNodeWithTag("energy_EXHAUSTED").assertIsSelected()
            compose.onNodeWithTag("sleepiness_SLEEPING").assertIsSelected()
            saveConditionCharacter("sleeping_${outfit.id}")
        }
        selectOption("preview_animations")
        selectOption("animation_WALKING")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("animation_WALKING").assertIsSelected()
        compose.onNodeWithTag("outfit_sport").assertIsSelected()
        saveConditionCharacter("walking_after_condition")
    }

    private fun selectOption(tag: String) {
        scrollTo(tag)
        compose.onNodeWithTag(tag).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(tag).assertIsSelected()
    }

    private fun scrollTo(tag: String) {
        // Semantics scrolling animates too: advance the manual test clock between scroll requests.
        repeat(3) {
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val top = node.positionInRoot.y
            val bottom = top + node.size.height
            if (top >= viewport.top && bottom <= viewport.bottom) return
            val delta = (top + bottom) / 2f - viewport.center.y
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, delta)
            }
            compose.mainClock.advanceTimeBy(1000)
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun saveConditionCharacter(name: String) {
        scrollTo("jive_character")
        saveCharacter(name)
    }

    private fun characterBitmap(): Bitmap = compose.onNodeWithTag("jive_character")
        .performScrollTo().captureToImage().asAndroidBitmap()

    private fun saveCharacter(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir(null)
        File(directory, "jive_$name.png").outputStream().use {
            characterBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class PlaygroundLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
