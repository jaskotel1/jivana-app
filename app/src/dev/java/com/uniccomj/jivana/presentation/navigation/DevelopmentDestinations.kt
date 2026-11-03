package com.uniccomj.jivana.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.uniccomj.jivana.R
import com.uniccomj.jivana.presentation.jiveplayground.JivePlaygroundScreen

private const val JivePlaygroundRoute = "dev/jive-playground"

fun NavGraphBuilder.developmentDestinations(navController: NavHostController) {
    composable(JivePlaygroundRoute) {
        JivePlaygroundScreen(onBack = { navController.popBackStack() })
    }
}

@Composable
fun DevelopmentHomeAction(navController: NavHostController, modifier: Modifier = Modifier) {
    TextButton(
        onClick = { navController.navigate(JivePlaygroundRoute) },
        modifier = modifier.testTag("jive_playground_open")
    ) {
        Text(stringResource(R.string.jive_playground_title))
    }
}
