package com.uniccomj.jivana.presentation.jiveplayground

import com.uniccomj.jivana.domain.model.JiveReaction
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private const val PhaseAverageWeight = 0.5f
private const val SmoothstepCoefficient = 3f

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
    internal const val RaisedArmAngle = 112f
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

    fun sample(
        animation: JiveAnimation,
        seconds: Float,
        into: JivePose,
        blinkSeconds: Float = seconds
    ) {
        into.setFrom(Neutral)
        val time = seconds.coerceAtLeast(0f)
        into.eyeOpenness = blink(blinkSeconds.coerceAtLeast(0f))
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
    var motionSeconds = 0f
        private set
    var blinkSeconds = 0f
        private set
    private var animationsEnabled = true
    private var celebrating = false
    private var visualSource = JiveVisualState()
    private var visualTarget = visualSource
    private var visualSeconds = JiveMotion.TransitionDuration
    private var transitionSeconds = JiveMotion.TransitionDuration
    private val source = JivePose()
    private val target = JivePose()
    private val rest = JivePose()
    private val celebration = JivePose()

    /** Repeated completions restart from the displayed pose instead of queuing animations. */
    fun react(reaction: JiveReaction) {
        if (reaction == JiveReaction.CELEBRATING && animationsEnabled) {
            select(JiveAnimation.HAPPY)
            celebrating = true
        }
    }

    fun select(next: JiveAnimation) {
        if (next == animation && next != JiveAnimation.HAPPY) return
        celebrating = false
        source.setFrom(pose)
        animation = next
        JiveMotion.sample(next, 0f, rest)
        animationSeconds = 0f
        motionSeconds = 0f
        transitionSeconds = 0f
        if (!animationsEnabled) renderStaticPose()
    }

    fun updateVisualState(next: JiveVisualState) {
        if (next == visualTarget) return
        // One snapshot per update, never an allocation per frame.
        visualSource = currentVisualState()
        visualTarget = next
        visualSeconds = 0f
        if (!animationsEnabled) renderStaticPose()
    }

    internal fun setAnimationsEnabled(enabled: Boolean) {
        if (animationsEnabled == enabled) return
        animationsEnabled = enabled
        if (enabled) {
            source.setFrom(pose)
            transitionSeconds = 0f
        } else {
            if (celebrating) select(JiveAnimation.IDLE)
            renderStaticPose()
        }
    }

    fun advance(seconds: Float) {
        require(seconds.isFinite() && seconds >= 0f)
        if (!animationsEnabled) return
        val oldSpeed = mix(visualSource.motionSpeed, visualTarget.motionSpeed, visualFraction())
        visualSeconds += seconds
        val speed = mix(visualSource.motionSpeed, visualTarget.motionSpeed, visualFraction())
        motionSeconds += seconds * (oldSpeed + speed) * PhaseAverageWeight
        blinkSeconds += seconds
        animationSeconds += seconds
        transitionSeconds += seconds
        val sampleSeconds = if (animation ==
            JiveAnimation.HAPPY
        ) {
            animationSeconds
        } else {
            motionSeconds
        }
        JiveMotion.sample(animation, sampleSeconds, target, blinkSeconds)
        if (celebrating) celebration.setFrom(target)
        applyVisualState(target)
        if (celebrating) {
            // The positive pose briefly takes precedence even over sadness or sleep.
            // Its envelope fades to zero, revealing the latest condition at the end.
            target.interpolate(target, celebration, celebration.expression)
        }
        pose.interpolate(source, target, easedFraction(transitionSeconds))
        if (animation == JiveAnimation.HAPPY && animationSeconds >= JiveMotion.HappyDuration) {
            select(JiveAnimation.IDLE)
        }
    }

    private fun renderStaticPose() {
        visualSeconds = JiveMotion.TransitionDuration
        transitionSeconds = JiveMotion.TransitionDuration
        // Rest pose instead of freezing a blink or a jump; playback phases are preserved.
        target.setFrom(rest)
        applyVisualState(target)
        pose.setFrom(target)
    }

    private fun visualFraction(): Float = easedFraction(visualSeconds)

    private fun easedFraction(seconds: Float): Float {
        val t = (seconds / JiveMotion.TransitionDuration).coerceIn(0f, 1f)
        return t * t * (SmoothstepCoefficient - 2f * t)
    }

    private fun currentVisualState(): JiveVisualState {
        val t = visualFraction()
        return JiveVisualState(
            expression = mix(visualSource.expression, visualTarget.expression, t),
            armRaise = mix(visualSource.armRaise, visualTarget.armRaise, t),
            bodyRotation = mix(visualSource.bodyRotation, visualTarget.bodyRotation, t),
            bodyHeightOffset = mix(visualSource.bodyHeightOffset, visualTarget.bodyHeightOffset, t),
            bodyY = mix(visualSource.bodyY, visualTarget.bodyY, t),
            shoulderDrop = mix(visualSource.shoulderDrop, visualTarget.shoulderDrop, t),
            motionSpeed = mix(visualSource.motionSpeed, visualTarget.motionSpeed, t),
            motionAmplitude = mix(visualSource.motionAmplitude, visualTarget.motionAmplitude, t),
            eyeOpenness = mix(visualSource.eyeOpenness, visualTarget.eyeOpenness, t),
            moodEyeOpenness = mix(visualSource.moodEyeOpenness, visualTarget.moodEyeOpenness, t),
            eyeTilt = mix(visualSource.eyeTilt, visualTarget.eyeTilt, t),
            gazeY = mix(visualSource.gazeY, visualTarget.gazeY, t),
            sleepMotionFactor = mix(
                visualSource.sleepMotionFactor,
                visualTarget.sleepMotionFactor,
                t
            )
        )
    }

    private fun applyVisualState(into: JivePose) {
        val t = visualFraction()
        val amplitude = mix(visualSource.motionAmplitude, visualTarget.motionAmplitude, t) *
            mix(visualSource.sleepMotionFactor, visualTarget.sleepMotionFactor, t)
        val arms = mix(visualSource.armRaise, visualTarget.armRaise, t)
        into.shoulderDrop = mix(visualSource.shoulderDrop, visualTarget.shoulderDrop, t)
        into.rootY = mix(rest.rootY, into.rootY, amplitude)
        into.bodyY =
            mix(rest.bodyY, into.bodyY, amplitude) + mix(visualSource.bodyY, visualTarget.bodyY, t)
        into.bodyRotation = mix(rest.bodyRotation, into.bodyRotation, amplitude) +
            mix(visualSource.bodyRotation, visualTarget.bodyRotation, t)
        into.bodyScaleX = mix(rest.bodyScaleX, into.bodyScaleX, amplitude)
        into.bodyScaleY = mix(rest.bodyScaleY, into.bodyScaleY, amplitude) +
            mix(visualSource.bodyHeightOffset, visualTarget.bodyHeightOffset, t)
        // Combined posture and motion stay inside the existing Happy animation envelope.
        into.leftArm = (mix(rest.leftArm, into.leftArm, amplitude) + arms)
            .coerceIn(-JiveMotion.RaisedArmAngle, JiveMotion.RaisedArmAngle)
        into.rightArm = (mix(rest.rightArm, into.rightArm, amplitude) - arms)
            .coerceIn(-JiveMotion.RaisedArmAngle, JiveMotion.RaisedArmAngle)
        into.leftLeg = mix(rest.leftLeg, into.leftLeg, amplitude)
        into.rightLeg = mix(rest.rightLeg, into.rightLeg, amplitude)
        into.leftFootLift = mix(rest.leftFootLift, into.leftFootLift, amplitude)
        into.rightFootLift = mix(rest.rightFootLift, into.rightFootLift, amplitude)
        into.eyeOpenness *= mix(visualSource.eyeOpenness, visualTarget.eyeOpenness, t)
        into.eyeOpenness *= mix(visualSource.moodEyeOpenness, visualTarget.moodEyeOpenness, t)
        into.eyeTilt += mix(visualSource.eyeTilt, visualTarget.eyeTilt, t)
        into.pupilY += mix(visualSource.gazeY, visualTarget.gazeY, t)
        into.expression =
            (into.expression + mix(visualSource.expression, visualTarget.expression, t))
                .coerceIn(-1f, 1f)
    }
}
