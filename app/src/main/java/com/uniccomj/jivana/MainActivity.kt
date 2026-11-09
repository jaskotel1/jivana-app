package com.uniccomj.jivana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.presentation.avatar.JiveConditionViewModel
import com.uniccomj.jivana.presentation.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val jiveConditionViewModel: JiveConditionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                jiveConditionViewModel.refreshWhileResumed()
            }
        }
        setContent {
            val jiveCondition by jiveConditionViewModel.condition.collectAsState()
            val refreshFailed by jiveConditionViewModel.refreshFailed.collectAsState()
            JivanaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(Modifier.padding(innerPadding)) {
                        if (refreshFailed) {
                            Text(stringResource(R.string.habit_schedule_error))
                            TextButton(onClick = jiveConditionViewModel::refreshForCurrentDay) {
                                Text(stringResource(R.string.habit_retry))
                            }
                        }
                        AppNavHost(
                            jiveCondition = jiveCondition,
                            jiveReactions = jiveConditionViewModel.reactions,
                            createHabitFactory = defaultViewModelProviderFactory
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    JivanaTheme {
        AppNavHost(jiveCondition = JiveCondition())
    }
}
