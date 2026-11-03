package com.uniccomj.jivana.presentation.jiveplayground

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

private const val ClockCycleSeconds = 60f
private const val ClockCycleMillis = 60_000

/** Shared playback clock, extracted unchanged from the DEV Playground. */
@Composable
internal fun JiveAnimationClock(animator: JiveAnimator, onFrame: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnFrame by rememberUpdatedState(onFrame)
    LaunchedEffect(animator, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (currentCoroutineContext().isActive) {
                // Respect disabled system animations without a zero-duration busy loop.
                if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ==
                    0f
                ) {
                    awaitCancellation()
                }
                var previous = 0f
                animate(
                    0f,
                    ClockCycleSeconds,
                    animationSpec = tween(ClockCycleMillis, easing = LinearEasing)
                ) { value, _ ->
                    animator.advance(value - previous)
                    previous = value
                    latestOnFrame()
                }
            }
        }
    }
}
