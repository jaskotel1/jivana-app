package com.uniccomj.jivana.domain.usecase

data class JiveScoringConfig(
    val moodRollingWindowDays: Int = 5,
    val ecstaticThreshold: Double = 0.90,
    val veryHappyThreshold: Double = 0.75,
    val happyThreshold: Double = 0.60,
    val neutralThreshold: Double = 0.40,
    val sadThreshold: Double = 0.25,
    val verySadThreshold: Double = 0.10,
    val weakEnergyThreshold: Double = 0.39,
    val energyRecoveryThreshold: Double = 0.60,
    val tiredAfterWeakDays: Int = 3,
    val veryTiredAfterWeakDays: Int = 7,
    val exhaustedAfterWeakDays: Int = 14,
    val energyRecoveryDaysPerLevel: Int = 5,
    val lowActivityThreshold: Double = 0.24,
    val sleepinessRecoveryThreshold: Double = 0.40,
    val sleepyAfterLowActivityDays: Int = 4,
    val verySleepyAfterLowActivityDays: Int = 8,
    val sleepingAfterInactiveDays: Int = 14,
    val sleepinessRecoveryDaysPerLevel: Int = 4
) {
    init {
        require(moodRollingWindowDays > 0)
        requireThresholdsAreDescending()
        require(weakEnergyThreshold in 0.0..1.0)
        require(energyRecoveryThreshold in 0.0..1.0)
        require(weakEnergyThreshold < energyRecoveryThreshold)
        require(lowActivityThreshold in 0.0..1.0)
        require(sleepinessRecoveryThreshold in 0.0..1.0)
        require(lowActivityThreshold < sleepinessRecoveryThreshold)
        require(tiredAfterWeakDays > 0)
        require(veryTiredAfterWeakDays > tiredAfterWeakDays)
        require(exhaustedAfterWeakDays > veryTiredAfterWeakDays)
        require(energyRecoveryDaysPerLevel > 0)
        require(sleepyAfterLowActivityDays > 0)
        require(verySleepyAfterLowActivityDays > sleepyAfterLowActivityDays)
        require(sleepingAfterInactiveDays > verySleepyAfterLowActivityDays)
        require(sleepinessRecoveryDaysPerLevel > 0)
    }

    private fun requireThresholdsAreDescending() {
        val moodThresholds = listOf(
            ecstaticThreshold,
            veryHappyThreshold,
            happyThreshold,
            neutralThreshold,
            sadThreshold,
            verySadThreshold
        )
        require(moodThresholds.all { it in 0.0..1.0 })
        require(moodThresholds.zipWithNext().all { (higher, lower) -> higher > lower })
    }
}
