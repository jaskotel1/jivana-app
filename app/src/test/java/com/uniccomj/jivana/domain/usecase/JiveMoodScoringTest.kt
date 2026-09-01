package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveMood
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class JiveMoodScoringTest {
    private val scorer = JiveConditionScorer()

    @ParameterizedTest
    @MethodSource("moodBoundaries")
    fun `completion boundaries map to expected mood`(completionPercent: Int, expected: JiveMood) {
        val history = listOf(day(index = 0, completionPercent = completionPercent))

        assertEquals(expected, scorer.score(history).mood)
    }

    @Test
    fun `one weak day does not collapse an established excellent mood`() {
        val history =
            days(count = 5, completionPercent = 100) + day(index = 5, completionPercent = 0)

        assertEquals(JiveMood.VERY_HAPPY, scorer.score(history).mood)
    }

    @Test
    fun `one excellent day does not immediately erase an established weak trend`() {
        val history =
            days(count = 5, completionPercent = 0) + day(index = 5, completionPercent = 100)

        assertEquals(JiveMood.VERY_SAD, scorer.score(history).mood)
    }

    private fun days(count: Int, completionPercent: Int): List<DailyHabitPerformance> =
        List(count) { index -> day(index, completionPercent) }

    private fun day(index: Int, completionPercent: Int) = DailyHabitPerformance(
        date = StartDate.plusDays(index.toLong()),
        plannedHabitCount = 100,
        completedHabitCount = completionPercent,
        activityRecorded = true
    )

    companion object {
        private val StartDate: LocalDate = LocalDate.of(2026, 1, 1)

        @JvmStatic
        fun moodBoundaries() = listOf(
            Arguments.of(100, JiveMood.ECSTATIC),
            Arguments.of(90, JiveMood.ECSTATIC),
            Arguments.of(89, JiveMood.VERY_HAPPY),
            Arguments.of(75, JiveMood.VERY_HAPPY),
            Arguments.of(74, JiveMood.HAPPY),
            Arguments.of(60, JiveMood.HAPPY),
            Arguments.of(59, JiveMood.NEUTRAL),
            Arguments.of(40, JiveMood.NEUTRAL),
            Arguments.of(39, JiveMood.SAD),
            Arguments.of(25, JiveMood.SAD),
            Arguments.of(24, JiveMood.VERY_SAD),
            Arguments.of(10, JiveMood.VERY_SAD),
            Arguments.of(9, JiveMood.DEVASTATED),
            Arguments.of(0, JiveMood.DEVASTATED)
        )
    }
}
