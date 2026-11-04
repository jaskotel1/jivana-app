package com.uniccomj.jivana.presentation.jiveplayground

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiveIdleEnergyTest {
    @Test
    fun `idle really applies every energy speed and amplitude without resetting phase`() {
        for (energy in JiveEnergy.entries) {
            val animator = JiveAnimator()
            animator.advance(0.8f)
            val state = JiveVisualStateResolver.resolve(JiveCondition(energy = energy))
            val before = animator.motionSeconds
            animator.updateVisualState(state)
            assertEquals(before, animator.motionSeconds)
            animator.advance(JiveMotion.TransitionDuration)
            val settled = animator.motionSeconds
            animator.advance(1f)
            assertEquals(settled + state.motionSpeed, animator.motionSeconds, 0.00001f)
            val sample = JivePose()
            JiveMotion.sample(
                JiveAnimation.IDLE,
                animator.motionSeconds,
                sample,
                animator.blinkSeconds
            )
            assertEquals(
                1f + (sample.bodyScaleY - 1f) * state.motionAmplitude + state.bodyHeightOffset,
                animator.pose.bodyScaleY,
                0.00001f
            )
            assertEquals(
                sample.leftArm * state.motionAmplitude + state.armRaise,
                animator.pose.leftArm,
                0.00001f
            )
            assertEquals(sample.expression, animator.pose.expression)
            assertEquals(sample.eyeOpenness, animator.pose.eyeOpenness)
        }
    }

    @Test
    fun `energy visibly lowers shoulders and combines posture without changing the face`() {
        val states = JiveEnergy.entries.map { energy ->
            JiveVisualStateResolver.resolve(JiveCondition(JiveMood.VERY_SAD, energy))
        }
        states.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.shoulderDrop > previous.shoulderDrop)
            assertTrue(next.bodyY > previous.bodyY)
            assertTrue(next.bodyHeightOffset < previous.bodyHeightOffset)
            assertEquals(previous.expression, next.expression)
            assertEquals(previous.eyeTilt, next.eyeTilt)
            assertEquals(previous.moodEyeOpenness, next.moodEyeOpenness)
            assertEquals(previous.gazeY, next.gazeY)
        }
        assertEquals(-3.5f, states.last().bodyRotation)
        assertEquals(7f, states.last().shoulderDrop)
    }

    @Test
    fun `all moods keep natural idle arms and have distinct face targets`() {
        val expressions = mutableSetOf<Float>()
        for (mood in JiveMood.entries) {
            val animator = JiveAnimator()
            animator.updateVisualState(JiveVisualStateResolver.resolve(JiveCondition(mood = mood)))
            animator.advance(1f)
            assertEquals(JiveAnimation.IDLE, animator.animation)
            assertTrue(kotlin.math.abs(animator.pose.leftArm) <= 2f)
            assertTrue(kotlin.math.abs(animator.pose.rightArm) <= 2f)
            expressions += animator.pose.expression
        }
        assertEquals(7, expressions.size)
    }
}
