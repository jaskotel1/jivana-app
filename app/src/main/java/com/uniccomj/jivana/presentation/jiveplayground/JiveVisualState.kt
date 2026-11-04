package com.uniccomj.jivana.presentation.jiveplayground

/** Visual targets only. Playback time, domain categories and clothes live elsewhere. */
data class JiveVisualState(
    val expression: Float = 0f,
    val armRaise: Float = 0f,
    val bodyRotation: Float = 0f,
    val bodyHeightOffset: Float = 0f,
    val bodyY: Float = 0f,
    val shoulderDrop: Float = 0f,
    val motionSpeed: Float = 1f,
    val motionAmplitude: Float = 1f,
    val eyeOpenness: Float = 1f,
    val moodEyeOpenness: Float = 1f,
    val eyeTilt: Float = 0f,
    val gazeY: Float = 0f,
    val sleepMotionFactor: Float = 1f
)
