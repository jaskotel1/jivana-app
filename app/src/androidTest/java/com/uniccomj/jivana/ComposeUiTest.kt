package com.uniccomj.jivana

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness
import com.uniccomj.jivana.presentation.components.JiveMascot
import org.junit.Rule
import org.junit.Test

class ComposeUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun textIsDisplayed() {
        composeTestRule.setContent {
            Text("Jivana")
        }

        composeTestRule
            .onNodeWithText("Jivana")
            .assertIsDisplayed()
    }

    @Test
    fun jiveMascotRendersDifferentConditionCombinations() {
        composeTestRule.setContent {
            Column {
                JiveMascot(
                    condition = JiveCondition(
                        mood = JiveMood.VERY_SAD,
                        energy = JiveEnergy.VERY_TIRED,
                        sleepiness = JiveSleepiness.VERY_SLEEPY
                    ),
                    modifier = Modifier.testTag("verySadTiredSleepyJive")
                )
                JiveMascot(
                    condition = JiveCondition(
                        mood = JiveMood.SAD,
                        energy = JiveEnergy.EXHAUSTED,
                        sleepiness = JiveSleepiness.SLEEPING
                    ),
                    modifier = Modifier.testTag("sadExhaustedSleepingJive")
                )
            }
        }

        composeTestRule.onNodeWithTag("verySadTiredSleepyJive").assertExists()
        composeTestRule.onNodeWithTag("sadExhaustedSleepingJive").assertExists()
    }
}
