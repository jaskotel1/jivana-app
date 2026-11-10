package com.uniccomj.jivana.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.uniccomj.jivana.presentation.home.AddHabitButton
import com.uniccomj.jivana.presentation.home.HabitManagementPanel
import com.uniccomj.jivana.presentation.home.HabitManagementViewModel
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
    createHabitFactory: ViewModelProvider.Factory? = null,
    habitManagement: HabitManagementViewModel? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(AppDestination.Home.route) {
            val openCreateHabit = {
                navController.navigate(AppDestination.CreateHabit.route) { launchSingleTop = true }
            }
            Column(Modifier.fillMaxSize()) {
                HomeScreen(
                    condition = jiveCondition,
                    modifier = Modifier.weight(1f),
                    reactions = jiveReactions
                ) {
                    if (habitManagement == null) {
                        AddHabitButton(openCreateHabit)
                    } else {
                        val state by habitManagement.state.collectAsState()
                        HabitManagementPanel(
                            state = state,
                            onCreateHabit = openCreateHabit,
                            onArchive = habitManagement::archive,
                            onResolveConflict = habitManagement::resolveConflict,
                            onStartCycle = habitManagement::startCycle
                        )
                    }
                }
                DevelopmentHomeAction(
                    navController = navController,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
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
