package com.uniccomj.jivana.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_day_interactions")
data class HabitDayInteractionEntity(@PrimaryKey val dateEpochDay: Long)
