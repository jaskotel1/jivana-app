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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.presentation.habitcreate.CreateHabitRoute
import com.uniccomj.jivana.presentation.habitcreate.CreateHabitViewModel
import com.uniccomj.jivana.presentation.home.HomeScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun AppNavHost(
    jiveCondition: JiveCondition,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppDestination = AppDestination.Home,
    jiveReactions: Flow<JiveReaction> = emptyFlow(),
    createHabitFactory: ViewModelProvider.Factory? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(AppDestination.Home.route) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    condition = jiveCondition,
                    reactions = jiveReactions,
                    onCreateHabit = {
                        navController.navigate(AppDestination.CreateHabit.route) {
                            launchSingleTop =
                                true
                        }
                    }
                )
                DevelopmentHomeAction(
                    navController = navController,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
        composable(AppDestination.Habits.route) {
            DestinationPlaceholder(titleRes = R.string.habits_title)
        }
        composable(AppDestination.CreateHabit.route) { entry ->
            // The Activity's Hilt factory consumes this entry's SavedStateHandle creation extras.
            val createHabitViewModel: CreateHabitViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = createHabitFactory
            )
            CreateHabitRoute(viewModel = createHabitViewModel, onBack = {
                navController.popBackStack()
            })
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
