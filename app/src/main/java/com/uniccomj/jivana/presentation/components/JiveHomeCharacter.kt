package com.uniccomj.jivana.presentation.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.presentation.jiveplayground.JiveAnimationClock
import com.uniccomj.jivana.presentation.jiveplayground.JiveAnimator
import com.uniccomj.jivana.presentation.jiveplayground.JiveCharacter
import com.uniccomj.jivana.presentation.jiveplayground.JiveVisualState
import com.uniccomj.jivana.presentation.jiveplayground.JiveWardrobe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val HomeCharacterAspectRatio = 1f

/** Condition affects pose targets; the default outfit and animator instance remain independent. */
@Composable
fun JiveHomeCharacter(
    visualState: JiveVisualState,
    modifier: Modifier = Modifier,
    reactions: Flow<JiveReaction> = emptyFlow()
) {
    val animator = remember { JiveAnimator() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(visualState) {
        animator.updateVisualState(visualState)
        frame++
    }
    JiveAnimationClock(animator) { frame++ }
    LaunchedEffect(animator, lifecycle, reactions) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            reactions.collect { reaction ->
                animator.react(reaction)
                frame++
            }
        }
    }
    val description = stringResource(R.string.avatar_title)
    JiveCharacter(
        pose = {
            @Suppress("UNUSED_EXPRESSION")
            frame // Frames invalidate drawing without recomposing the Home screen.
            animator.pose
        },
        outfit = JiveWardrobe.Default,
        modifier = modifier.aspectRatio(HomeCharacterAspectRatio)
            .semantics { contentDescription = description }
            .testTag("home_jive")
    )
}
