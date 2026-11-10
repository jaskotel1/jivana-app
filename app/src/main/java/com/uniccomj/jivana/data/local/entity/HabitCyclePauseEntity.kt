package com.uniccomj.jivana.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** An interval is keyed by its first paused day; the resume day is active again. */
@Entity(
    tableName = "habit_cycle_pauses",
    primaryKeys = ["cycleId", "startEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"]
        )
    ]
)
data class HabitCyclePauseEntity(
    val cycleId: String,
    val startEpochDay: Long,
    val resumeEpochDay: Long?
)
