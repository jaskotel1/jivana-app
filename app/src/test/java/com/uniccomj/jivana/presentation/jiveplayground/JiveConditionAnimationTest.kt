package com.uniccomj.jivana.presentation.jiveplayground

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class JiveConditionAnimationTest {
    @ParameterizedTest
    @MethodSource(
        "com.uniccomj.jivana.presentation.jiveplayground.JiveVisualStateResolverTest#conditions"
    )
    fun `every condition stays finite within the existing pose envelope`(condition: JiveCondition) {
        for (animation in JiveAnimation.entries) {
            val animator = JiveAnimator()
            animator.select(animation)
            animator.updateVisualState(JiveVisualStateResolver.resolve(condition))
            repeat(600) {
                animator.advance(1f / 60f)
                assertTrue(values(animator.pose).all(Float::isFinite))
                assertTrue(animator.pose.eyeOpenness in 0f..1f)
                assertTrue(animator.pose.expression in -1f..1f)
                assertTrue(animator.pose.bodyScaleX in 0.9f..1.1f)
                assertTrue(animator.pose.bodyScaleY in 0.9f..1.1f)
                assertTrue(animator.pose.rootY in -58f..0f)
                assertTrue(abs(animator.pose.leftArm) <= JiveMotion.RaisedArmAngle)
                assertTrue(abs(animator.pose.rightArm) <= JiveMotion.RaisedArmAngle)
            }
        }
    }

    @Test
    fun `condition transitions use real playback time and ignore identical targets`() {
        val animator = JiveAnimator()
        val state = JiveVisualState(expression = 1f, motionSpeed = 0.45f)
        animator.updateVisualState(state)
        assertEquals(0f, animator.pose.expression)
        animator.advance(JiveMotion.TransitionDuration / 2f)
        assertEquals(0.5f, animator.pose.expression, 0.00001f)
        animator.updateVisualState(state)
        animator.advance(JiveMotion.TransitionDuration / 2f)
        assertEquals(1f, animator.pose.expression, 0.00001f)
        assertEquals(JiveAnimation.IDLE, animator.animation)
    }

    @Test
    fun `retargeting any running preset preserves the displayed pose and all phases`() {
        for (animation in JiveAnimation.entries) {
            val animator = JiveAnimator()
            animator.select(animation)
            animator.advance(0.12f)
            animator.updateVisualState(JiveVisualState(expression = 1f))
            animator.advance(0.08f)
            val before = values(animator.pose)
            val phases =
                listOf(animator.animationSeconds, animator.motionSeconds, animator.blinkSeconds)
            animator.updateVisualState(
                JiveVisualStateResolver.resolve(
                    JiveCondition(
                        JiveMood.DEVASTATED,
                        JiveEnergy.EXHAUSTED,
                        JiveSleepiness.VERY_SLEEPY
                    )
                )
            )
            assertEquals(before, values(animator.pose))
            animator.advance(0f)
            assertClose(before, values(animator.pose))
            assertEquals(
                phases,
                listOf(animator.animationSeconds, animator.motionSeconds, animator.blinkSeconds)
            )
            animator.advance(0.0001f)
            before.zip(values(animator.pose)).forEach { (old, new) ->
                assertTrue(abs(new - old) < 0.1f)
            }
        }
    }

    @Test
    fun `energy changes integrate motion phase without changing blink time`() {
        val animator = JiveAnimator()
        animator.select(JiveAnimation.WALKING)
        animator.advance(2f)
        animator.updateVisualState(JiveVisualState(motionSpeed = 0.45f, motionAmplitude = 0.35f))
        animator.advance(JiveMotion.TransitionDuration)
        val phase = animator.motionSeconds
        animator.advance(1f)
        assertEquals(phase + 0.45f, animator.motionSeconds, 0.00001f)
        assertEquals(3f + JiveMotion.TransitionDuration, animator.blinkSeconds, 0.00001f)
        val sampled = JivePose()
        JiveMotion.sample(
            JiveAnimation.WALKING,
            animator.motionSeconds,
            sampled,
            animator.blinkSeconds
        )
        assertEquals(sampled.leftLeg * 0.35f, animator.pose.leftLeg, 0.00001f)
        assertEquals(sampled.eyeOpenness, animator.pose.eyeOpenness)
    }

    @Test
    fun `amplitude changes movement without shrinking the resting sad posture`() {
        val animator = JiveAnimator()
        animator.select(JiveAnimation.SAD)
        animator.updateVisualState(JiveVisualState(motionAmplitude = 0f))
        animator.advance(1f)
        assertEquals(0.98f, animator.pose.bodyScaleY)
        assertEquals(-3f, animator.pose.bodyRotation)
        assertEquals(-5f, animator.pose.leftArm)
        assertEquals(-1f, animator.pose.expression)
    }

    @Test
    fun `sleep multiplies blinking and suppresses movement even with normal energy`() {
        val awake = JiveAnimator()
        val sleepy = JiveAnimator()
        val sleeping = JiveAnimator()
        sleepy.updateVisualState(
            JiveVisualStateResolver.resolve(JiveCondition(sleepiness = JiveSleepiness.SLEEPY))
        )
        sleeping.updateVisualState(
            JiveVisualStateResolver.resolve(JiveCondition(sleepiness = JiveSleepiness.SLEEPING))
        )
        repeat(300) {
            awake.advance(1f / 60f)
            sleepy.advance(1f / 60f)
            sleeping.advance(1f / 60f)
            if (it > 30) {
                assertEquals(awake.pose.eyeOpenness * 0.75f, sleepy.pose.eyeOpenness, 0.00001f)
                assertEquals(0f, sleeping.pose.eyeOpenness)
                assertEquals(
                    (awake.pose.bodyScaleY - 1f) * 0.15f,
                    sleeping.pose.bodyScaleY - 1f,
                    0.00001f
                )
            }
        }
    }

    @Test
    fun `disabled animations show updated resting condition and preserve paused phases`() {
        val animator = JiveAnimator()
        animator.select(JiveAnimation.WALKING)
        animator.advance(0.7f)
        val phase = animator.motionSeconds
        val blink = animator.blinkSeconds
        animator.setAnimationsEnabled(false)
        animator.updateVisualState(
            JiveVisualStateResolver.resolve(JiveCondition(mood = JiveMood.ECSTATIC))
        )
        assertEquals(1f, animator.pose.expression)
        assertEquals(0f, animator.pose.leftArm)
        assertEquals(0.8f, animator.pose.eyeOpenness)
        val static = values(animator.pose)
        animator.advance(5f)
        assertEquals(static, values(animator.pose))
        assertEquals(phase, animator.motionSeconds)
        assertEquals(blink, animator.blinkSeconds)
        animator.setAnimationsEnabled(true)
        animator.advance(0f)
        assertClose(static, values(animator.pose))
        animator.advance(0.1f)
        assertEquals(phase + 0.1f, animator.motionSeconds, 0.00001f)
    }

    @Test
    fun `happy still finishes on time and returns to the latest persistent condition`() {
        val animator = JiveAnimator()
        animator.select(JiveAnimation.HAPPY)
        animator.advance(0.4f)
        animator.updateVisualState(
            JiveVisualStateResolver.resolve(JiveCondition(JiveMood.VERY_SAD, JiveEnergy.EXHAUSTED))
        )
        repeat(80) { animator.advance(1f / 60f) }
        assertEquals(JiveAnimation.IDLE, animator.animation)
        assertEquals(-0.75f, animator.pose.expression, 0.00001f)
        assertEquals(-3.5f, animator.pose.bodyRotation, 0.00001f)
    }

    private fun assertClose(expected: List<Float>, actual: List<Float>) {
        expected.zip(actual).forEach { (a, b) -> assertEquals(a, b, 0.00001f) }
    }

    private fun values(pose: JivePose): List<Float> = with(pose) {
        listOf(
            rootY, bodyY, bodyRotation, bodyScaleX, bodyScaleY, leftArm, rightArm,
            leftLeg, rightLeg, leftFootLift, rightFootLift, eyeOpenness, pupilX, pupilY, expression,
            shoulderDrop, eyeTilt
        )
    }
}
