package com.uniccomj.jivana.presentation.jiveplayground

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.domain.model.JiveSleepiness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiveReactionAnimationTest {
    @Test
    fun `celebration is positive even when devastated exhausted and sleeping`() {
        val animator = JiveAnimator()
        animator.updateVisualState(
            JiveVisualStateResolver.resolve(
                JiveCondition(JiveMood.DEVASTATED, JiveEnergy.EXHAUSTED, JiveSleepiness.SLEEPING)
            )
        )
        animator.advance(1f)
        animator.react(JiveReaction.CELEBRATING)
        animator.advance(JiveMotion.HappyDuration / 2f)
        assertTrue(animator.pose.rootY < -50f)
        assertTrue(animator.pose.expression > 0.9f)
        assertTrue(animator.pose.eyeOpenness > 0.9f)
        assertTrue(animator.pose.leftArm > 100f)

        animator.updateVisualState(
            JiveVisualStateResolver.resolve(JiveCondition(JiveMood.SAD, JiveEnergy.TIRED))
        )
        repeat(90) { animator.advance(1f / 60f) }
        assertEquals(JiveAnimation.IDLE, animator.animation)
        assertEquals(-0.4f, animator.pose.expression, 0.0001f)
        assertEquals(-0.4f, animator.pose.bodyRotation, 0.0001f)
        assertEquals(0f, animator.pose.rootY, 0.0001f)
    }

    @Test
    fun `repeated celebrations restart smoothly and never build a backlog`() {
        val animator = JiveAnimator()
        animator.react(JiveReaction.CELEBRATING)
        animator.advance(0.4f)
        val jump = animator.pose.rootY
        val arm = animator.pose.leftArm
        animator.react(JiveReaction.CELEBRATING)
        animator.advance(0f)
        assertEquals(jump, animator.pose.rootY)
        assertEquals(arm, animator.pose.leftArm)
        assertEquals(0f, animator.animationSeconds)
        animator.react(JiveReaction.NONE)
        assertEquals(JiveAnimation.HAPPY, animator.animation)
        repeat(100) { animator.advance(1f / 60f) }
        assertEquals(JiveAnimation.IDLE, animator.animation)
    }

    @Test
    fun `disabled animations discard new and running reactions without replay`() {
        val animator = JiveAnimator()
        animator.react(JiveReaction.CELEBRATING)
        animator.advance(0.4f)
        animator.setAnimationsEnabled(false)
        animator.react(JiveReaction.CELEBRATING)
        assertEquals(JiveAnimation.IDLE, animator.animation)
        assertEquals(0f, animator.pose.rootY)
        animator.setAnimationsEnabled(true)
        animator.advance(0.5f)
        assertEquals(JiveAnimation.IDLE, animator.animation)
    }
}
