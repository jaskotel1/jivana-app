package com.uniccomj.jivana.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.presentation.home.HomeScreen

@Composable
fun AppNavHost(
    jiveCondition: JiveCondition,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppDestination = AppDestination.Home
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(AppDestination.Home.route) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(condition = jiveCondition)
                DevelopmentHomeAction(
                    navController = navController,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
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
        developmentDestinations(navController)
    }
}

@Composable
private fun DestinationPlaceholder(@StringRes titleRes: Int) {
    Text(text = stringResource(titleRes))
}
