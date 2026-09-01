package com.uniccomj.jivana.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "habit_schedules",
    primaryKeys = ["habitId", "dateEpochDay"]
)
data class HabitScheduleEntity(val habitId: String, val dateEpochDay: Long)
