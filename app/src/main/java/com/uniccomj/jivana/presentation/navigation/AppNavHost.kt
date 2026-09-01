package com.uniccomj.jivana.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uniccomj.jivana.R
import com.uniccomj.jivana.presentation.avatar.JiveConditionController
import com.uniccomj.jivana.presentation.home.HomeScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppDestination = AppDestination.Home
) {
    val jiveConditionController = remember { JiveConditionController() }
    val jiveCondition by jiveConditionController.condition.collectAsState()

    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(AppDestination.Home.route) {
            HomeScreen(condition = jiveCondition)
        }
        composable(AppDestination.Habits.route) {
            DestinationPlaceholder(titleRes = R.string.habits_title)
        }
        composable(AppDestination.Avatar.route) {
            DestinationPlaceholder(titleRes = R.string.avatar_title)
        }
        composable(AppDestination.Settings.route) {
            DestinationPlaceholder(titleRes = R.string.settings_title)
        }
    }
}

@Composable
private fun DestinationPlaceholder(@StringRes titleRes: Int) {
    Text(text = stringResource(titleRes))
}
