package com.uniccomj.jivana.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uniccomj.jivana.data.local.dao.HabitCycleDao
import com.uniccomj.jivana.data.local.dao.HabitDao
import com.uniccomj.jivana.data.local.dao.HabitHistoryDao
import com.uniccomj.jivana.data.local.entity.HabitCompletionEntity
import com.uniccomj.jivana.data.local.entity.HabitCycleEntity
import com.uniccomj.jivana.data.local.entity.HabitCyclePauseEntity
import com.uniccomj.jivana.data.local.entity.HabitDayInteractionEntity
import com.uniccomj.jivana.data.local.entity.HabitEntity
import com.uniccomj.jivana.data.local.entity.HabitScheduleEntity

@Database(
    entities = [
        HabitScheduleEntity::class,
        HabitCompletionEntity::class,
        HabitDayInteractionEntity::class,
        HabitEntity::class,
        HabitCycleEntity::class,
        HabitCyclePauseEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class JivanaDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitHistoryDao(): HabitHistoryDao
    abstract fun habitCycleDao(): HabitCycleDao
}
