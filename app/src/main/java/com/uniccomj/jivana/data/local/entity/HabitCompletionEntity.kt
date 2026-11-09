package com.uniccomj.jivana.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "dateEpochDay"]
)
data class HabitCompletionEntity(val habitId: String, val dateEpochDay: Long)
