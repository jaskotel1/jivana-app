package com.uniccomj.jivana

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}
