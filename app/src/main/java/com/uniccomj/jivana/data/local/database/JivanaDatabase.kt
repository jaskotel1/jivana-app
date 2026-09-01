package com.uniccomj.jivana.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uniccomj.jivana.data.local.dao.HabitHistoryDao
import com.uniccomj.jivana.data.local.entity.HabitCompletionEntity
import com.uniccomj.jivana.data.local.entity.HabitDayInteractionEntity
import com.uniccomj.jivana.data.local.entity.HabitScheduleEntity

@Database(
    entities = [
        HabitScheduleEntity::class,
        HabitCompletionEntity::class,
        HabitDayInteractionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class JivanaDatabase : RoomDatabase() {
    abstract fun habitHistoryDao(): HabitHistoryDao
}
