package com.uniccomj.jivana.presentation.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.JiveCondition
import org.junit.Rule
import org.junit.Test

class ProdNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun prodHomeDisplaysCanvasWithoutPlaygroundEntry() {
        compose.mainClock.autoAdvance = false
        compose.setContent { JivanaTheme { AppNavHost(jiveCondition = JiveCondition()) } }
        compose.onNodeWithTag("home_jive").assertIsDisplayed()
        compose.onNodeWithTag("jive_playground_open").assertDoesNotExist()
    }
}
