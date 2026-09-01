package com.uniccomj.jivana.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiveConditionTest {
    @Test
    fun `default condition is neutral normal awake with no reaction`() {
        val condition = JiveCondition()

        assertEquals(JiveMood.NEUTRAL, condition.mood)
        assertEquals(JiveEnergy.NORMAL, condition.energy)
        assertEquals(JiveSleepiness.AWAKE, condition.sleepiness)
        assertEquals(JiveReaction.NONE, condition.reaction)
    }

    @Test
    fun `condition combines very sad very tired and very sleepy`() {
        val condition = JiveCondition(
            mood = JiveMood.VERY_SAD,
            energy = JiveEnergy.VERY_TIRED,
            sleepiness = JiveSleepiness.VERY_SLEEPY
        )

        assertEquals(JiveMood.VERY_SAD, condition.mood)
        assertEquals(JiveEnergy.VERY_TIRED, condition.energy)
        assertEquals(JiveSleepiness.VERY_SLEEPY, condition.sleepiness)
    }

    @Test
    fun `condition combines sad exhausted and sleeping`() {
        val condition = JiveCondition(
            mood = JiveMood.SAD,
            energy = JiveEnergy.EXHAUSTED,
            sleepiness = JiveSleepiness.SLEEPING
        )

        assertEquals(JiveMood.SAD, condition.mood)
        assertEquals(JiveEnergy.EXHAUSTED, condition.energy)
        assertEquals(JiveSleepiness.SLEEPING, condition.sleepiness)
    }
}
