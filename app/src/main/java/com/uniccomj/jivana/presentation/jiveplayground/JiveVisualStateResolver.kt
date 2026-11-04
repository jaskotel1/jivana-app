package com.uniccomj.jivana.presentation.jiveplayground

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness

/** Pure presentation mapping; intentionally ignores transient reactions. */
@Suppress("MagicNumber") // Visual tuning, independent of scoring thresholds.
object JiveVisualStateResolver {
    fun resolve(condition: JiveCondition): JiveVisualState {
        val mood = moodState(condition.mood)
        val energy = energyState(condition.energy)
        val eyeOpenness = when (condition.sleepiness) {
            JiveSleepiness.AWAKE -> 1f
            JiveSleepiness.SLEEPY -> 0.75f
            JiveSleepiness.VERY_SLEEPY -> 0.4f
            JiveSleepiness.SLEEPING -> 0f
        }
        return mood.copy(
            bodyRotation = mood.bodyRotation + energy.bodyRotation,
            bodyHeightOffset = mood.bodyHeightOffset + energy.bodyHeightOffset,
            bodyY = mood.bodyY + energy.bodyY,
            armRaise = energy.armRaise,
            shoulderDrop = energy.shoulderDrop,
            motionSpeed = energy.motionSpeed,
            motionAmplitude = energy.motionAmplitude,
            eyeOpenness = eyeOpenness,
            sleepMotionFactor = if (condition.sleepiness == JiveSleepiness.SLEEPING) 0.15f else 1f
        )
    }

    private fun moodState(mood: JiveMood): JiveVisualState = when (mood) {
        JiveMood.NEUTRAL -> JiveVisualState()

        JiveMood.HAPPY -> JiveVisualState(expression = 0.35f, gazeY = -1f)

        JiveMood.VERY_HAPPY -> JiveVisualState(expression = 0.7f, gazeY = -2f)

        JiveMood.ECSTATIC -> JiveVisualState(
            expression = 1f,
            moodEyeOpenness = 0.8f,
            eyeTilt = 4f,
            gazeY = -2f
        )

        JiveMood.SAD -> JiveVisualState(expression = -0.4f, eyeTilt = -2f, gazeY = 2f)

        JiveMood.VERY_SAD -> JiveVisualState(
            expression = -0.75f,
            moodEyeOpenness = 0.88f,
            eyeTilt = -8f,
            gazeY = 6f,
            bodyRotation = -1.5f,
            bodyHeightOffset = -0.01f
        )

        JiveMood.DEVASTATED -> JiveVisualState(
            expression = -1f,
            moodEyeOpenness = 0.72f,
            eyeTilt = -13f,
            gazeY = 10f,
            bodyRotation = -3f,
            bodyHeightOffset = -0.02f,
            bodyY = 2f
        )
    }

    private fun energyState(energy: JiveEnergy): JiveVisualState = when (energy) {
        JiveEnergy.NORMAL -> JiveVisualState()

        JiveEnergy.TIRED -> JiveVisualState(
            motionSpeed = 0.85f,
            motionAmplitude = 0.8f,
            shoulderDrop = 2f,
            bodyY = 2f,
            bodyHeightOffset = -0.006f,
            bodyRotation = -0.4f,
            armRaise = -1.5f
        )

        JiveEnergy.VERY_TIRED -> JiveVisualState(
            motionSpeed = 0.65f,
            motionAmplitude = 0.6f,
            shoulderDrop = 4f,
            bodyY = 4f,
            bodyHeightOffset = -0.014f,
            bodyRotation = -1.2f,
            armRaise = -3f
        )

        JiveEnergy.EXHAUSTED -> JiveVisualState(
            motionSpeed = 0.45f,
            motionAmplitude = 0.35f,
            shoulderDrop = 7f,
            bodyY = 7f,
            bodyHeightOffset = -0.025f,
            bodyRotation = -2f,
            armRaise = -5f
        )
    }
}
