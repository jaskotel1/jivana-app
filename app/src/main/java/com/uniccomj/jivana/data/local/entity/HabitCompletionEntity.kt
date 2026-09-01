package com.uniccomj.jivana.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "dateEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitScheduleEntity::class,
            parentColumns = ["habitId", "dateEpochDay"],
            childColumns = ["habitId", "dateEpochDay"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HabitCompletionEntity(val habitId: String, val dateEpochDay: Long)
