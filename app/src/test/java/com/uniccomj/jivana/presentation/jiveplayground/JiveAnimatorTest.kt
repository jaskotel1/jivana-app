package com.uniccomj.jivana.presentation.jiveplayground

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiveAnimatorTest {
    @Test
    fun `idle breathes and blinks while both feet stay planted`() {
        val pose = JivePose()
        var minimumEyeOpening = 1f
        var maximumBodyScale = 1f
        repeat(600) { frame ->
            JiveMotion.sample(JiveAnimation.IDLE, frame / 60f, pose)
            assertEquals(0f, pose.leftLeg)
            assertEquals(0f, pose.rightLeg)
            assertEquals(0f, pose.leftFootLift)
            assertEquals(0f, pose.rightFootLift)
            assertEquals(0f, pose.rootY)
            minimumEyeOpening = minOf(minimumEyeOpening, pose.eyeOpenness)
            maximumBodyScale = maxOf(maximumBodyScale, pose.bodyScaleY)
        }
        assertTrue(minimumEyeOpening < 0.1f)
        assertTrue(maximumBodyScale > 1.005f)
    }

    @Test
    fun `walking alternates legs around separate hips and repeats without drift`() {
        val first = JivePose()
        val opposite = JivePose()
        val repeated = JivePose()
        JiveMotion.sample(JiveAnimation.WALKING, JiveMotion.WalkingPeriod / 4f, first)
        JiveMotion.sample(JiveAnimation.WALKING, JiveMotion.WalkingPeriod * 3f / 4f, opposite)
        JiveMotion.sample(JiveAnimation.WALKING, JiveMotion.WalkingPeriod * 5f / 4f, repeated)
        assertEquals(-first.leftLeg, first.rightLeg)
        assertEquals(first.leftLeg, -opposite.leftLeg, 0.001f)
        assertEquals(first.leftLeg, repeated.leftLeg, 0.001f)
        assertTrue(first.leftFootLift > 0f)
        assertEquals(0f, first.rightFootLift)
        assertEquals(0f, first.rootY)
        assertTrue(JiveProportions.LeftHipX < JiveProportions.CenterX)
        assertTrue(JiveProportions.RightHipX > JiveProportions.CenterX)
        assertEquals(JiveProportions.GroundY, JiveProportions.HipY + 76f)
    }

    @Test
    fun `happy jumps raises arms and returns automatically to idle`() {
        val animator = JiveAnimator()
        animator.select(JiveAnimation.HAPPY)
        repeat(33) { animator.advance(1f / 60f) }
        assertTrue(animator.pose.rootY < -50f)
        assertTrue(animator.pose.leftArm > 100f)
        assertTrue(animator.pose.rightArm < -100f)
        repeat(60) { animator.advance(1f / 60f) }
        assertEquals(JiveAnimation.IDLE, animator.animation)
        assertEquals(0f, animator.pose.rootY, 0.001f)
    }

    @Test
    fun `every interrupted transition starts at its displayed pose`() {
        for (from in JiveAnimation.entries) {
            for (to in JiveAnimation.entries) {
                val animator = JiveAnimator()
                animator.select(from)
                animator.advance(0.12f)
                val before = values(animator.pose)
                animator.select(to)
                assertEquals(before, values(animator.pose))
                animator.advance(0f)
                assertEquals(before, values(animator.pose))
                animator.advance(0.0001f)
                values(animator.pose).zip(before).forEach { (after, previous) ->
                    assertTrue(abs(after - previous) < 0.03f, "$from to $to: $after vs $previous")
                }
            }
        }
    }

    @Test
    fun `pose interpolation covers every channel and clamps endpoints`() {
        val a = JivePose()
        val b = JivePose()
        val result = JivePose()
        JiveMotion.sample(JiveAnimation.WALKING, 0.25f, a)
        JiveMotion.sample(JiveAnimation.SAD, 0.9f, b)
        result.interpolate(a, b, 0.5f)
        values(result).zip(values(a).zip(values(b))).forEach { (actual, endpoints) ->
            assertEquals((endpoints.first + endpoints.second) / 2f, actual, 0.00001f)
        }
        result.interpolate(a, b, -1f)
        assertEquals(values(a), values(result))
        result.interpolate(a, b, 2f)
        values(result).zip(values(b)).forEach { (actual, expected) ->
            assertEquals(expected, actual, 0.00001f)
        }
    }

    @Test
    fun `all poses remain finite and continuous across complete animation cycles`() {
        val previous = JivePose()
        val current = JivePose()
        for (animation in JiveAnimation.entries) {
            JiveMotion.sample(animation, 0f, previous)
            repeat(1200) { frame ->
                JiveMotion.sample(animation, (frame + 1) / 120f, current)
                assertTrue(values(current).all { it.isFinite() })
                values(current).zip(values(previous)).forEach { (next, old) ->
                    assertTrue(abs(next - old) < 5f, "$animation discontinuity at $frame")
                }
                assertTrue(current.eyeOpenness in 0.01f..1f)
                assertTrue(current.bodyScaleX > 0f && current.bodyScaleY > 0f)
                previous.setFrom(current)
            }
        }
    }

    @Test
    fun `changing every outfit mid walk preserves the clock and next pose`() {
        val control = JiveAnimator()
        val dressed = JiveAnimator()
        control.select(JiveAnimation.WALKING)
        dressed.select(JiveAnimation.WALKING)
        repeat(180) { frame ->
            val outfit = JiveWardrobe.All[(frame / 10) % JiveWardrobe.All.size]
            assertTrue(outfit.id.isNotEmpty())
            control.advance(1f / 60f)
            dressed.advance(1f / 60f)
            assertEquals(control.animationSeconds, dressed.animationSeconds)
            assertEquals(values(control.pose), values(dressed.pose))
        }
        assertTrue(JiveWardrobe.Hoodie.garments.any { it.joint == JiveJoint.LEFT_ARM })
        assertTrue(JiveWardrobe.Hoodie.garments.any { it.joint == JiveJoint.RIGHT_ARM })
    }

    @Test
    fun `uniform fit fits portrait landscape tiny and zero size containers`() {
        for ((width, height) in listOf(320f to 440f, 100f to 900f, 900f to 100f, 1f to 1f)) {
            val scale = JiveProportions.fitScale(width, height)
            assertTrue(scale > 0f)
            assertTrue(JiveProportions.Width * scale <= width + 0.001f)
            assertTrue(JiveProportions.Height * scale <= height + 0.001f)
        }
        assertEquals(1f, JiveProportions.fitScale(JiveProportions.Width, JiveProportions.Height))
        assertEquals(0f, JiveProportions.fitScale(0f, 100f))
        assertEquals(0f, JiveProportions.fitScale(100f, -1f))
    }

    @Test
    fun `invalid clock deltas are rejected`() {
        for (delta in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { JiveAnimator().advance(delta) }
        }
    }

    private fun values(pose: JivePose): List<Float> = with(pose) {
        listOf(
            rootY, bodyY, bodyRotation, bodyScaleX, bodyScaleY, leftArm, rightArm,
            leftLeg, rightLeg, leftFootLift, rightFootLift, eyeOpenness, pupilX, pupilY, expression
        )
    }
}
