package com.uniccomj.jivana.presentation.jiveplayground

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

private const val ClockCycleSeconds = 60f
private const val ClockCycleMillis = 60_000

/** Lifecycle-bound playback; a zero system scale renders current visual targets without a frame loop. */
@Composable
internal fun JiveAnimationClock(animator: JiveAnimator, onFrame: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnFrame by rememberUpdatedState(onFrame)
    LaunchedEffect(animator, lifecycle) {
        val durationScale = currentCoroutineContext()[MotionDurationScale]
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
                animator.setAnimationsEnabled(scale > 0f)
                latestOnFrame()
                if (scale > 0f) {
                    while (currentCoroutineContext().isActive) {
                        var previous = 0f
                        animate(
                            0f,
                            ClockCycleSeconds,
                            animationSpec = tween(ClockCycleMillis, easing = LinearEasing)
                        ) { value, _ ->
                            // A scale change may arrive before collectLatest cancels this cycle.
                            if (durationScale == null || durationScale.scaleFactor == scale) {
                                animator.advance(value - previous)
                                previous = value
                                latestOnFrame()
                            }
                        }
                    }
                }
            }
        }
    }
}
