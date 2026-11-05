package com.uniccomj.jivana.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val scheduledDaysMask: Int,
    val startEpochDay: Long,
    val archivedAtEpochMillis: Long?,
    val scheduledThroughEpochDay: Long
)
