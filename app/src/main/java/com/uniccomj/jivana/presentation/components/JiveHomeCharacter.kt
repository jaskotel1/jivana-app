package com.uniccomj.jivana.presentation.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.uniccomj.jivana.R
import com.uniccomj.jivana.presentation.jiveplayground.JiveAnimationClock
import com.uniccomj.jivana.presentation.jiveplayground.JiveAnimator
import com.uniccomj.jivana.presentation.jiveplayground.JiveCharacter
import com.uniccomj.jivana.presentation.jiveplayground.JiveWardrobe

private const val HomeCharacterAspectRatio = 1f

/** Home deliberately uses Idle / Default, independently of the business condition. */
@Composable
fun JiveHomeCharacter(modifier: Modifier = Modifier) {
    val animator = remember { JiveAnimator() }
    var frame by remember { mutableFloatStateOf(0f) }
    JiveAnimationClock(animator) { frame += 1f }
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
