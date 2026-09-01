package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.DailyHabitPerformance
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.domain.model.JiveSleepiness

class JiveConditionScorer(private val config: JiveScoringConfig = JiveScoringConfig()) {
    fun score(history: List<DailyHabitPerformance>): JiveCondition {
        val scoredDays = history
            .associateBy(DailyHabitPerformance::date)
            .values
            .filter { it.completionRate != null }
            .sortedBy(DailyHabitPerformance::date)

        if (scoredDays.isEmpty()) return JiveCondition()

        return JiveCondition(
            mood = scoreMood(scoredDays),
            energy = scoreEnergy(scoredDays),
            sleepiness = scoreSleepiness(scoredDays),
            reaction = JiveReaction.NONE
        )
    }

    private fun scoreMood(history: List<DailyHabitPerformance>): JiveMood {
        val averageCompletion = history
            .takeLast(config.moodRollingWindowDays)
            .mapNotNull(DailyHabitPerformance::completionRate)
            .average()

        return when {
            averageCompletion >= config.ecstaticThreshold -> JiveMood.ECSTATIC
            averageCompletion >= config.veryHappyThreshold -> JiveMood.VERY_HAPPY
            averageCompletion >= config.happyThreshold -> JiveMood.HAPPY
            averageCompletion >= config.neutralThreshold -> JiveMood.NEUTRAL
            averageCompletion >= config.sadThreshold -> JiveMood.SAD
            averageCompletion >= config.verySadThreshold -> JiveMood.VERY_SAD
            else -> JiveMood.DEVASTATED
        }
    }

    private fun scoreEnergy(history: List<DailyHabitPerformance>): JiveEnergy {
        var severity = EnergySeverity.NORMAL
        var weakDayCount = 0
        var recoveryDayCount = 0

        history.forEach { day ->
            val completionRate = requireNotNull(day.completionRate)
            when {
                completionRate <= config.weakEnergyThreshold -> {
                    weakDayCount += 1
                    recoveryDayCount = 0
                    severity = maxOf(severity, energySeverityFor(weakDayCount))
                }

                completionRate >= config.energyRecoveryThreshold -> {
                    weakDayCount = 0
                    recoveryDayCount += 1
                    if (recoveryDayCount == config.energyRecoveryDaysPerLevel) {
                        severity = severity.recoverOneLevel()
                        recoveryDayCount = 0
                    }
                }

                else -> {
                    weakDayCount = 0
                    recoveryDayCount = 0
                }
            }
        }

        return severity.energy
    }

    private fun energySeverityFor(weakDayCount: Int): EnergySeverity = when {
        weakDayCount >= config.exhaustedAfterWeakDays -> EnergySeverity.EXHAUSTED
        weakDayCount >= config.veryTiredAfterWeakDays -> EnergySeverity.VERY_TIRED
        weakDayCount >= config.tiredAfterWeakDays -> EnergySeverity.TIRED
        else -> EnergySeverity.NORMAL
    }

    private fun scoreSleepiness(history: List<DailyHabitPerformance>): JiveSleepiness {
        var severity = SleepinessSeverity.AWAKE
        var lowActivityDayCount = 0
        var inactiveDayCount = 0
        var recoveryDayCount = 0

        history.forEach { day ->
            val completionRate = requireNotNull(day.completionRate)
            when {
                !day.activityRecorded -> {
                    lowActivityDayCount += 1
                    inactiveDayCount += 1
                    recoveryDayCount = 0
                    severity = maxOf(
                        severity,
                        sleepinessSeverityFor(lowActivityDayCount, inactiveDayCount)
                    )
                }

                completionRate <= config.lowActivityThreshold -> {
                    lowActivityDayCount += 1
                    inactiveDayCount = 0
                    recoveryDayCount = 0
                    severity = maxOf(
                        severity,
                        sleepinessSeverityFor(lowActivityDayCount, inactiveDayCount)
                    )
                }

                completionRate >= config.sleepinessRecoveryThreshold -> {
                    lowActivityDayCount = 0
                    inactiveDayCount = 0
                    recoveryDayCount += 1
                    if (recoveryDayCount == config.sleepinessRecoveryDaysPerLevel) {
                        severity = severity.recoverOneLevel()
                        recoveryDayCount = 0
                    }
                }

                else -> {
                    lowActivityDayCount = 0
                    inactiveDayCount = 0
                    recoveryDayCount = 0
                }
            }
        }

        return severity.sleepiness
    }

    private fun sleepinessSeverityFor(
        lowActivityDayCount: Int,
        inactiveDayCount: Int
    ): SleepinessSeverity = when {
        inactiveDayCount >= config.sleepingAfterInactiveDays -> SleepinessSeverity.SLEEPING

        lowActivityDayCount >= config.verySleepyAfterLowActivityDays ->
            SleepinessSeverity.VERY_SLEEPY

        lowActivityDayCount >= config.sleepyAfterLowActivityDays -> SleepinessSeverity.SLEEPY

        else -> SleepinessSeverity.AWAKE
    }

    private enum class EnergySeverity(val energy: JiveEnergy) {
        NORMAL(JiveEnergy.NORMAL),
        TIRED(JiveEnergy.TIRED),
        VERY_TIRED(JiveEnergy.VERY_TIRED),
        EXHAUSTED(JiveEnergy.EXHAUSTED);

        fun recoverOneLevel(): EnergySeverity = entries[maxOf(ordinal - 1, 0)]
    }

    private enum class SleepinessSeverity(val sleepiness: JiveSleepiness) {
        AWAKE(JiveSleepiness.AWAKE),
        SLEEPY(JiveSleepiness.SLEEPY),
        VERY_SLEEPY(JiveSleepiness.VERY_SLEEPY),
        SLEEPING(JiveSleepiness.SLEEPING);

        fun recoverOneLevel(): SleepinessSeverity = entries[maxOf(ordinal - 1, 0)]
    }
}
