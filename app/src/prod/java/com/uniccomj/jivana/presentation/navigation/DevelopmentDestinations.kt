package com.uniccomj.jivana.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

// PROD shares the Canvas engine in main, but registers no development UI or routes.
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.developmentDestinations(navController: NavHostController) = Unit

@Suppress("UNUSED_PARAMETER")
@Composable
fun DevelopmentHomeAction(navController: NavHostController, modifier: Modifier = Modifier) = Unit
