package com.uniccomj.jivana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.presentation.avatar.JiveConditionViewModel
import com.uniccomj.jivana.presentation.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val jiveConditionViewModel: JiveConditionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val jiveCondition by jiveConditionViewModel.condition.collectAsState()
            JivanaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        jiveCondition = jiveCondition,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        jiveConditionViewModel.refreshForCurrentDay()
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    JivanaTheme {
        AppNavHost(jiveCondition = JiveCondition())
    }
}
