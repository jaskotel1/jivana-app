package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiveHistoryOptimizationEquivalenceTest {
    private val config = JiveScoringConfig()
    private val scorer = JiveConditionScorer(config)

    @Test
    fun `optimized suffix matches full histories of 90 180 and 365 scoring days`() {
        listOf(90, 180, 365).forEach { length ->
            val histories = listOf(
                days(length) { 100 to true },
                days(length) { 0 to false },
                days(length) { 0 to true },
                days(length) { index ->
                    when {
                        index < length - 25 -> 50 to true
                        index < length - 11 -> 0 to false
                        else -> 100 to true
                    }
                },
                days(length) { index ->
                    when (index % 37) {
                        in 0..13 -> 0 to false
                        in 14..28 -> 100 to true
                        else -> 30 to true
                    }
                }
            )

            histories.forEach { assertEquivalent(it) }
        }
    }

    @Test
    fun `rare weekly schedules retain scoring days across calendar gaps`() {
        val fullHistory = buildList {
            repeat(365) { index ->
                val scheduled = index % 7 == 0
                add(
                    performance(
                        index = index,
                        completionPercent = if (scheduled) (index * 13) % 101 else 0,
                        activityRecorded = scheduled,
                        planned = scheduled
                    )
                )
            }
        }

        assertEquivalent(fullHistory)
        assertEquals(53, fullHistory.count { it.completionRate != null })
    }

    @Test
    fun `arbitrarily old exhausted state is retained when no later sequence resets it`() {
        val fullHistory = days(14) { 0 to false } +
            days(351, startIndex = 14) { 50 to true }
        val optimized = optimizedHistory(fullHistory)

        assertEquals(fullHistory, optimized)
        assertEquivalent(fullHistory)
    }

    @Test
    fun `stable recent history is bounded by synchronization sequences`() {
        val fullHistory = days(365) { 100 to true }
        val optimized = optimizedHistory(fullHistory)

        assertEquals(config.energyFullRecoveryDays, optimized.size)
        assertTrue(optimized.size < fullHistory.size)
        assertEquivalent(fullHistory)
    }

    private fun assertEquivalent(fullHistory: List<DailyHabitPerformance>) {
        assertEquals(scorer.score(fullHistory), scorer.score(optimizedHistory(fullHistory)))
    }

    private fun optimizedHistory(
        history: List<DailyHabitPerformance>
    ): List<DailyHabitPerformance> {
        val scoredDays = history
            .associateBy(DailyHabitPerformance::date)
            .values
            .filter { it.completionRate != null }
            .sortedBy(DailyHabitPerformance::date)
        val energyStart = latestWindowStart(
            scoredDays,
            config.energyFullRecoveryDays to { window ->
                window.all {
                    requireNotNull(it.completionRate) >= config.energyRecoveryThreshold
                }
            },
            config.exhaustedAfterWeakDays to { window ->
                window.all { requireNotNull(it.completionRate) <= config.weakEnergyThreshold }
            }
        )
        val sleepinessStart = latestWindowStart(
            scoredDays,
            config.sleepinessFullRecoveryDays to { window ->
                window.all {
                    requireNotNull(it.completionRate) >= config.sleepinessRecoveryThreshold
                }
            },
            config.sleepingAfterInactiveDays to { window ->
                window.all { !it.activityRecorded }
            }
        )
        if (energyStart == null || sleepinessStart == null) return scoredDays

        val moodStart = (scoredDays.size - config.moodRollingWindowDays).coerceAtLeast(0)
        return scoredDays.drop(minOf(energyStart, sleepinessStart, moodStart))
    }

    private fun latestWindowStart(
        history: List<DailyHabitPerformance>,
        vararg windows: Pair<Int, (List<DailyHabitPerformance>) -> Boolean>
    ): Int? = history.indices.reversed().firstOrNull { start ->
        windows.any { (size, matches) ->
            start + size <= history.size && matches(history.subList(start, start + size))
        }
    }

    private fun days(
        count: Int,
        startIndex: Int = 0,
        values: (Int) -> Pair<Int, Boolean>
    ): List<DailyHabitPerformance> = List(count) { offset ->
        val (completion, activity) = values(offset)
        performance(startIndex + offset, completion, activity)
    }

    private fun performance(
        index: Int,
        completionPercent: Int,
        activityRecorded: Boolean,
        planned: Boolean = true
    ) = DailyHabitPerformance(
        date = StartDate.plusDays(index.toLong()),
        plannedHabitCount = if (planned) 100 else 0,
        completedHabitCount = if (planned) completionPercent else 0,
        activityRecorded = activityRecorded
    )

    private companion object {
        val StartDate: LocalDate = LocalDate.of(2025, 1, 1)
    }
}
