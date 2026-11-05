package com.uniccomj.jivana.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uniccomj.jivana.R
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.presentation.components.JiveHomeCharacter
import com.uniccomj.jivana.presentation.jiveplayground.JiveVisualStateResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val JiveWidthFraction = 0.72f

@Composable
fun HomeScreen(
    condition: JiveCondition,
    modifier: Modifier = Modifier,
    reactions: Flow<JiveReaction> = emptyFlow()
) {
    val visualState = remember(condition) { JiveVisualStateResolver.resolve(condition) }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.home_title))
        Spacer(modifier = Modifier.height(24.dp))
        JiveHomeCharacter(
            visualState = visualState,
            reactions = reactions,
            modifier = Modifier
                .fillMaxWidth(JiveWidthFraction)
                .widthIn(max = 280.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    JivanaTheme {
        HomeScreen(condition = JiveCondition())
    }
}
