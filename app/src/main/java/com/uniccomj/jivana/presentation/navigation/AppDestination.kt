package com.uniccomj.jivana.presentation.navigation

sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object Habits : AppDestination("habits")
    data object Avatar : AppDestination("avatar")
    data object Settings : AppDestination("settings")
}
