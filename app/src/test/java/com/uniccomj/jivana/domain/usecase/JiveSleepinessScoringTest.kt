package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveSleepiness
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiveSleepinessScoringTest {
    private val scorer = JiveConditionScorer()

    @Test
    fun `one inactive day keeps Jive awake`() {
        assertEquals(
            JiveSleepiness.AWAKE,
            scorer.score(days(1, activityRecorded = false)).sleepiness
        )
    }

    @Test
    fun `four low activity days make Jive sleepy`() {
        assertEquals(
            JiveSleepiness.SLEEPY,
            scorer.score(days(4, completionPercent = 20)).sleepiness
        )
    }

    @Test
    fun `eight low activity days make Jive very sleepy`() {
        assertEquals(
            JiveSleepiness.VERY_SLEEPY,
            scorer.score(days(8, completionPercent = 20)).sleepiness
        )
    }

    @Test
    fun `fourteen inactive days make Jive sleep`() {
        assertEquals(
            JiveSleepiness.SLEEPING,
            scorer.score(days(14, activityRecorded = false)).sleepiness
        )
    }

    @Test
    fun `recorded low completion is distinguished from no activity`() {
        val recordedLowCompletion = scorer.score(days(14, completionPercent = 20)).sleepiness
        val noActivity = scorer.score(days(14, activityRecorded = false)).sleepiness

        assertEquals(JiveSleepiness.VERY_SLEEPY, recordedLowCompletion)
        assertEquals(JiveSleepiness.SLEEPING, noActivity)
    }

    @Test
    fun `sleeping state recovers one level per four active days`() {
        val inactiveHistory = days(14, activityRecorded = false)

        assertEquals(
            JiveSleepiness.SLEEPING,
            scorer.score(inactiveHistory + days(3, 100, true, startIndex = 14)).sleepiness
        )
        assertEquals(
            JiveSleepiness.VERY_SLEEPY,
            scorer.score(inactiveHistory + days(4, 100, true, startIndex = 14)).sleepiness
        )
        assertEquals(
            JiveSleepiness.SLEEPY,
            scorer.score(inactiveHistory + days(8, 100, true, startIndex = 14)).sleepiness
        )
        assertEquals(
            JiveSleepiness.AWAKE,
            scorer.score(inactiveHistory + days(12, 100, true, startIndex = 14)).sleepiness
        )
    }

    @Test
    fun `inactivity during recovery makes Jive sleep again`() {
        val history = days(14, activityRecorded = false) +
            days(8, 100, true, startIndex = 14) +
            days(14, activityRecorded = false, startIndex = 22)

        assertEquals(JiveSleepiness.SLEEPING, scorer.score(history).sleepiness)
    }

    private fun days(
        count: Int,
        completionPercent: Int = 0,
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
