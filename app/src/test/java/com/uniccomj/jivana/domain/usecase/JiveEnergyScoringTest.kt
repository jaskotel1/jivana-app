package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveEnergy
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiveEnergyScoringTest {
    private val scorer = JiveConditionScorer()

    @Test
    fun `one weak day keeps normal energy`() {
        assertEnergy(JiveEnergy.NORMAL, weakDays = 1)
    }

    @Test
    fun `three consecutive weak days cause tired energy`() {
        assertEnergy(JiveEnergy.TIRED, weakDays = 3)
    }

    @Test
    fun `seven consecutive weak days cause very tired energy`() {
        assertEnergy(JiveEnergy.VERY_TIRED, weakDays = 7)
    }

    @Test
    fun `fourteen consecutive weak days cause exhausted energy`() {
        assertEnergy(JiveEnergy.EXHAUSTED, weakDays = 14)
    }

    @Test
    fun `exhausted energy recovers one level per five strong days`() {
        val weakHistory = days(count = 14, completionPercent = 0)

        assertEquals(
            JiveEnergy.EXHAUSTED,
            scorer.score(weakHistory + days(4, 100, startIndex = 14)).energy
        )
        assertEquals(
            JiveEnergy.VERY_TIRED,
            scorer.score(weakHistory + days(5, 100, startIndex = 14)).energy
        )
        assertEquals(
            JiveEnergy.TIRED,
            scorer.score(weakHistory + days(10, 100, startIndex = 14)).energy
        )
        assertEquals(
            JiveEnergy.NORMAL,
            scorer.score(weakHistory + days(15, 100, startIndex = 14)).energy
        )
    }

    @Test
    fun `weak days during recovery worsen energy again`() {
        val history = days(14, 0) +
            days(10, 100, startIndex = 14) +
            days(7, 0, startIndex = 24)

        assertEquals(JiveEnergy.VERY_TIRED, scorer.score(history).energy)
    }

    private fun assertEnergy(expected: JiveEnergy, weakDays: Int) {
        assertEquals(expected, scorer.score(days(weakDays, 0)).energy)
    }

    private fun days(
        count: Int,
        completionPercent: Int,
        startIndex: Int = 0
    ): List<DailyHabitPerformance> = List(count) { offset ->
        DailyHabitPerformance(
            date = StartDate.plusDays((startIndex + offset).toLong()),
            plannedHabitCount = 100,
            completedHabitCount = completionPercent,
            activityRecorded = completionPercent > 0
        )
    }

    private companion object {
        val StartDate: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
