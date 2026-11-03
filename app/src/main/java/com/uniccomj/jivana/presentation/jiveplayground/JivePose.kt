package com.uniccomj.jivana.presentation.jiveplayground

import kotlin.math.min

enum class JiveAnimation { IDLE, HAPPY, WALKING, SAD }

/** Reused by the animator and renderer; all distances use the character's design space. */
class JivePose {
    var rootY = 0f
    var bodyY = 0f
    var bodyRotation = 0f
    var bodyScaleX = 1f
    var bodyScaleY = 1f
    var leftArm = 0f
    var rightArm = 0f
    var leftLeg = 0f
    var rightLeg = 0f
    var leftFootLift = 0f
    var rightFootLift = 0f
    var eyeOpenness = 1f
    var pupilX = 0f
    var pupilY = 0f
    var expression = 0f

    fun setFrom(other: JivePose) = interpolate(other, other, 0f)

    fun interpolate(from: JivePose, to: JivePose, fraction: Float) {
        val t = fraction.coerceIn(0f, 1f)
        rootY = mix(from.rootY, to.rootY, t)
        bodyY = mix(from.bodyY, to.bodyY, t)
        bodyRotation = mix(from.bodyRotation, to.bodyRotation, t)
        bodyScaleX = mix(from.bodyScaleX, to.bodyScaleX, t)
        bodyScaleY = mix(from.bodyScaleY, to.bodyScaleY, t)
        leftArm = mix(from.leftArm, to.leftArm, t)
        rightArm = mix(from.rightArm, to.rightArm, t)
        leftLeg = mix(from.leftLeg, to.leftLeg, t)
        rightLeg = mix(from.rightLeg, to.rightLeg, t)
        leftFootLift = mix(from.leftFootLift, to.leftFootLift, t)
        rightFootLift = mix(from.rightFootLift, to.rightFootLift, t)
        eyeOpenness = mix(from.eyeOpenness, to.eyeOpenness, t)
        pupilX = mix(from.pupilX, to.pupilX, t)
        pupilY = mix(from.pupilY, to.pupilY, t)
        expression = mix(from.expression, to.expression, t)
    }
}

internal fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/** Single source of proportions, joint locations and the safe animation envelope. */
object JiveProportions {
    const val Width = 360f
    const val Height = 480f
    const val FramePadding = 20f
    const val CenterX = 160f
    const val BodyTop = 86f
    const val BodyBottom = 342f
    const val BodyLeft = 70f
    const val BodyRight = 250f
    const val ShoulderY = 245f
    const val LeftShoulderX = 77f
    const val RightShoulderX = 243f
    const val HipY = 329f
    const val LeftHipX = 124f
    const val RightHipX = 196f
    const val GroundY = 405f
    const val EyeY = 178f
    const val LeftEyeX = 126f
    const val RightEyeX = 194f
    const val EyeWidth = 54f
    const val EyeHeight = 78f
    const val Outline = 4f

    fun fitScale(width: Float, height: Float): Float =
        if (width <= 0f || height <= 0f) 0f else min(width / Width, height / Height)
}
