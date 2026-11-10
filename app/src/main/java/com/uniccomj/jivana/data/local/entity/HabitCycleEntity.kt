package com.uniccomj.jivana.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_cycles",
    foreignKeys = [
        ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"])
    ],
    indices = [Index(value = ["habitId"], unique = true)]
)
data class HabitCycleEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val startEpochDay: Long,
    val archivedEpochDay: Long? = null
)
