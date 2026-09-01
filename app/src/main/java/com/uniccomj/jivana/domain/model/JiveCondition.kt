package com.uniccomj.jivana.domain.model

data class JiveCondition(
    val mood: JiveMood = JiveMood.NEUTRAL,
    val energy: JiveEnergy = JiveEnergy.NORMAL,
    val sleepiness: JiveSleepiness = JiveSleepiness.AWAKE,
    val reaction: JiveReaction = JiveReaction.NONE
)

enum class JiveMood {
    NEUTRAL,
    HAPPY,
    VERY_HAPPY,
    ECSTATIC,
    SAD,
    VERY_SAD,
    DEVASTATED
}

enum class JiveEnergy {
    NORMAL,
    TIRED,
    VERY_TIRED,
    EXHAUSTED
}

enum class JiveSleepiness {
    AWAKE,
    SLEEPY,
    VERY_SLEEPY,
    SLEEPING
}

enum class JiveReaction {
    NONE,
    CELEBRATING
}
