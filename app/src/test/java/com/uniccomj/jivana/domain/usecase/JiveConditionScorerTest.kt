package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.domain.model.JiveSleepiness
import com.uniccomj.jivana.presentation.avatar.JiveConditionController
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiveConditionScorerTest {
    private val scorer = JiveConditionScorer()

    @Test
    fun `new user without history receives default condition`() {
        assertEquals(JiveCondition(), scorer.score(emptyList()))
    }

    @Test
    fun `days without planned habits are neutral and excluded from scoring`() {
        val history = List(20) { index ->
            DailyHabitPerformance(
                date = StartDate.plusDays(index.toLong()),
                plannedHabitCount = 0,
                completedHabitCount = 0,
                activityRecorded = false
            )
        }

        assertEquals(JiveCondition(), scorer.score(history))
    }

    @Test
    fun `very sad very tired and sleepy dimensions coexist`() {
        val condition = scorer.score(
            days(count = 7, completionPercent = 20, activityRecorded = true)
        )

        assertEquals(JiveMood.VERY_SAD, condition.mood)
        assertEquals(JiveEnergy.VERY_TIRED, condition.energy)
        assertEquals(JiveSleepiness.SLEEPY, condition.sleepiness)
    }

    @Test
    fun `devastated exhausted and sleeping dimensions coexist`() {
        val condition = scorer.score(
            days(count = 14, completionPercent = 0, activityRecorded = false)
        )

        assertEquals(JiveMood.DEVASTATED, condition.mood)
        assertEquals(JiveEnergy.EXHAUSTED, condition.energy)
        assertEquals(JiveSleepiness.SLEEPING, condition.sleepiness)
    }

    @Test
    fun `happy tired and awake dimensions coexist`() {
        val history = days(count = 3, completionPercent = 30) +
            days(count = 4, completionPercent = 75, startIndex = 3)

        val condition = scorer.score(history)

        assertEquals(JiveMood.HAPPY, condition.mood)
        assertEquals(JiveEnergy.TIRED, condition.energy)
        assertEquals(JiveSleepiness.AWAKE, condition.sleepiness)
    }

    @Test
    fun `short perfect history is handled without negative inertia`() {
        val condition = scorer.score(days(count = 1, completionPercent = 100))

        assertEquals(JiveMood.ECSTATIC, condition.mood)
        assertEquals(JiveEnergy.NORMAL, condition.energy)
        assertEquals(JiveSleepiness.AWAKE, condition.sleepiness)
        assertEquals(JiveReaction.NONE, condition.reaction)
    }

    @Test
    fun `full completion over several days keeps best mood and healthy supporting dimensions`() {
        val condition = scorer.score(days(count = 10, completionPercent = 100))

        assertEquals(JiveMood.ECSTATIC, condition.mood)
        assertEquals(JiveEnergy.NORMAL, condition.energy)
        assertEquals(JiveSleepiness.AWAKE, condition.sleepiness)
    }

    @Test
    fun `scored condition can be published by controller`() {
        val controller = JiveConditionController()
        val scoredCondition = scorer.score(days(count = 7, completionPercent = 20))

        controller.updateCondition(scoredCondition)

        assertEquals(scoredCondition, controller.condition.value)
    }

    private fun days(
        count: Int,
        completionPercent: Int,
        activityRecorded: Boolean = true,
        startIndex: Int = 0
    ): List<DailyHabitPerformance> = List(count) { offset ->
        DailyHabitPerformance(
            date = StartDate.plusDays((startIndex + offset).toLong()),
            plannedHabitCount = 100,
            completedHabitCount = completionPercent,
            activityRecorded = activityRecorded
        )
    }

    private companion object {
        val StartDate: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
