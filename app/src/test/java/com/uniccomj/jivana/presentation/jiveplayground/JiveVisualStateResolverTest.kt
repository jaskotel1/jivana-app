package com.uniccomj.jivana.presentation.jiveplayground

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.domain.model.JiveSleepiness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class JiveVisualStateResolverTest {
    @Test
    fun `default preserves the original neutral character`() {
        assertEquals(JiveVisualState(), JiveVisualStateResolver.resolve(JiveCondition()))
    }

    @Test
    fun `every mood has its own expression and appropriate resting posture`() {
        val expressions = mapOf(
            JiveMood.NEUTRAL to 0f,
            JiveMood.HAPPY to 0.35f,
            JiveMood.VERY_HAPPY to 0.7f,
            JiveMood.ECSTATIC to 1f,
            JiveMood.SAD to -0.4f,
            JiveMood.VERY_SAD to -0.75f,
            JiveMood.DEVASTATED to -1f
        )
        assertEquals(JiveMood.entries.toSet(), expressions.keys)
        expressions.forEach { (mood, expression) ->
            assertEquals(
                expression,
                JiveVisualStateResolver.resolve(JiveCondition(mood = mood)).expression
            )
        }
        JiveMood.entries.forEach { mood ->
            assertEquals(0f, JiveVisualStateResolver.resolve(JiveCondition(mood = mood)).armRaise)
        }
        assertEquals(
            0.8f,
            JiveVisualStateResolver.resolve(JiveCondition(mood = JiveMood.ECSTATIC)).moodEyeOpenness
        )
        assertEquals(
            0f,
            JiveVisualStateResolver.resolve(JiveCondition(mood = JiveMood.HAPPY)).bodyRotation
        )
        assertEquals(
            -3f,
            JiveVisualStateResolver.resolve(JiveCondition(mood = JiveMood.DEVASTATED)).bodyRotation
        )
    }

    @Test
    fun `every energy maps to the specified independent speed and amplitude`() {
        val expected = listOf(1f to 1f, 0.85f to 0.8f, 0.65f to 0.6f, 0.45f to 0.35f)
        assertEquals(JiveEnergy.entries.size, expected.size)
        JiveEnergy.entries.zip(expected).forEach { (energy, values) ->
            val state = JiveVisualStateResolver.resolve(JiveCondition(energy = energy))
            assertEquals(values.first, state.motionSpeed)
            assertEquals(values.second, state.motionAmplitude)
        }
    }

    @Test
    fun `sleepiness progressively closes eyes and only sleeping suppresses motion`() {
        val expected = listOf(1f, 0.75f, 0.4f, 0f)
        assertEquals(JiveSleepiness.entries.size, expected.size)
        JiveSleepiness.entries.zip(expected).forEach { (sleepiness, eyes) ->
            val state = JiveVisualStateResolver.resolve(JiveCondition(sleepiness = sleepiness))
            assertEquals(eyes, state.eyeOpenness)
            assertEquals(
                if (sleepiness ==
                    JiveSleepiness.SLEEPING
                ) {
                    0.15f
                } else {
                    1f
                },
                state.sleepMotionFactor
            )
        }
    }

    @ParameterizedTest
    @MethodSource("conditions")
    fun `all 112 combinations are deterministic and keep dimensions independent`(
        condition: JiveCondition
    ) {
        val state = JiveVisualStateResolver.resolve(condition)
        val mood = JiveVisualStateResolver.resolve(JiveCondition(mood = condition.mood))
        val energy = JiveVisualStateResolver.resolve(JiveCondition(energy = condition.energy))
        val sleep = JiveVisualStateResolver.resolve(
            JiveCondition(sleepiness = condition.sleepiness)
        )
        assertEquals(
            mood.copy(
                bodyRotation = mood.bodyRotation + energy.bodyRotation,
                bodyHeightOffset = mood.bodyHeightOffset + energy.bodyHeightOffset,
                bodyY = mood.bodyY + energy.bodyY,
                armRaise = energy.armRaise,
                shoulderDrop = energy.shoulderDrop,
                motionSpeed = energy.motionSpeed,
                motionAmplitude = energy.motionAmplitude,
                eyeOpenness = sleep.eyeOpenness,
                sleepMotionFactor = sleep.sleepMotionFactor
            ),
            state
        )
        assertEquals(state, JiveVisualStateResolver.resolve(condition))
        JiveReaction.entries.forEach { reaction ->
            assertEquals(
                state,
                JiveVisualStateResolver.resolve(condition.copy(reaction = reaction))
            )
        }
    }

    companion object {
        @JvmStatic
        fun conditions(): List<JiveCondition> = JiveMood.entries.flatMap { mood ->
            JiveEnergy.entries.flatMap { energy ->
                JiveSleepiness.entries.map { sleepiness -> JiveCondition(mood, energy, sleepiness) }
            }
        }
    }
}
