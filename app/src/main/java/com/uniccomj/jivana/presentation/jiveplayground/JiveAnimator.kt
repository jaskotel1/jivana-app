package com.uniccomj.jivana.presentation.jiveplayground

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** Pure pose sampling: intentionally has no knowledge of clothes, Compose or app avatar states. */
object JiveMotion {
    const val HappyDuration = 1.1f
    const val TransitionDuration = 0.32f
    const val WalkingPeriod = 1.2f
    private val Neutral = JivePose()
    private const val IdleBreathPeriod = 3.6f
    private const val IdleWidthBreath = 0.009f
    private const val IdleHeightBreath = 0.007f
    private const val IdleArmSway = 2f
    private const val JumpHeight = 58f
    private const val RaisedArmAngle = 112f
    private const val JumpStretch = 0.035f
    private const val LegSwing = 24f
    private const val FootLift = 10f
    private const val ArmSwing = 20f
    private const val WalkSway = 1.5f
    private const val WalkBob = 2f
    private const val WalkGaze = 3f
    private const val SadBreathPeriod = 5f
    private const val SadTilt = -3f
    private const val SadHeight = 0.98f
    private const val SadBreath = 0.004f
    private const val SadArmAngle = 5f
    private const val SadEyeOpening = 0.8f
    private const val SadGaze = 10f
    private const val BlinkPeriod = 4.7f
    private const val BlinkTime = 3.9f
    private const val BlinkHalfDuration = 0.12f
    private const val MinimumEyeOpening = 0.04f

    fun sample(animation: JiveAnimation, seconds: Float, into: JivePose) {
        into.setFrom(Neutral)
        val time = seconds.coerceAtLeast(0f)
        into.eyeOpenness = blink(time)
        when (animation) {
            JiveAnimation.IDLE -> {
                val breath = wave(time / IdleBreathPeriod)
                into.bodyScaleX = 1f + IdleWidthBreath * breath
                into.bodyScaleY = 1f + IdleHeightBreath * breath
                into.leftArm = IdleArmSway * breath
                into.rightArm = -IdleArmSway * breath
            }

            JiveAnimation.HAPPY -> {
                val pulse = sin(PI * (time / HappyDuration).coerceIn(0f, 1f)).toFloat()
                val jump = pulse * pulse
                into.rootY = -JumpHeight * jump
                into.leftArm = RaisedArmAngle * jump
                into.rightArm = -RaisedArmAngle * jump
                into.bodyScaleX = 1f - JumpStretch * jump
                into.bodyScaleY = 1f + JumpStretch * jump
                into.expression = jump
            }

            JiveAnimation.WALKING -> {
                val stride = wave(time / WalkingPeriod)
                into.leftLeg = LegSwing * stride
                into.rightLeg = -LegSwing * stride
                into.leftFootLift = FootLift * max(0f, stride)
                into.rightFootLift = FootLift * max(0f, -stride)
                into.leftArm = -ArmSwing * stride
                into.rightArm = ArmSwing * stride
                into.bodyY = -WalkBob * (1f - wave(time / (WalkingPeriod / 2f)))
                into.bodyRotation = WalkSway * stride
                into.pupilX = WalkGaze
            }

            JiveAnimation.SAD -> {
                val breath = wave(time / SadBreathPeriod)
                into.bodyRotation = SadTilt
                into.bodyScaleY = SadHeight + SadBreath * breath
                into.leftArm = -SadArmAngle + breath
                into.rightArm = SadArmAngle - breath
                into.eyeOpenness *= SadEyeOpening
                into.pupilY = SadGaze
                into.expression = -1f
            }
        }
    }

    private fun blink(seconds: Float): Float {
        val phase = seconds % BlinkPeriod
        val distance = kotlin.math.abs(phase - BlinkTime)
        return (distance / BlinkHalfDuration).coerceIn(MinimumEyeOpening, 1f)
    }

    private fun wave(turns: Float): Float = sin(2.0 * PI * turns).toFloat()
}

/** Interrupted transitions start at the currently displayed pose; buffers are allocated only once. */
class JiveAnimator {
    val pose = JivePose()
    var animation = JiveAnimation.IDLE
        private set
    var animationSeconds = 0f
        private set
    private var transitionSeconds = JiveMotion.TransitionDuration
    private val source = JivePose()
    private val target = JivePose()

    fun select(next: JiveAnimation) {
        if (next == animation && next != JiveAnimation.HAPPY) return
        source.setFrom(pose)
        animation = next
        animationSeconds = 0f
        transitionSeconds = 0f
    }

    fun advance(seconds: Float) {
        require(seconds.isFinite() && seconds >= 0f)
        animationSeconds += seconds
        transitionSeconds += seconds
        JiveMotion.sample(animation, animationSeconds, target)
        val t = (transitionSeconds / JiveMotion.TransitionDuration).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        pose.interpolate(source, target, eased)
        if (animation == JiveAnimation.HAPPY && animationSeconds >= JiveMotion.HappyDuration) {
            select(JiveAnimation.IDLE)
        }
    }
}
