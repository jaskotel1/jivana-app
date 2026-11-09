package com.uniccomj.jivana.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uniccomj.jivana.data.local.entity.HabitEntity

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): HabitEntity?

    @Query(
        """
        SELECT * FROM habits
        WHERE archivedAtEpochMillis IS NULL
          AND scheduleType != 'WEEKLY_TARGET'
          AND scheduledThroughEpochDay < :throughEpochDay
        """
    )
    suspend fun getPendingSchedules(throughEpochDay: Long): List<HabitEntity>

    @Query("UPDATE habits SET scheduledThroughEpochDay = :throughEpochDay WHERE id = :id")
    suspend fun updateScheduleProgress(id: String, throughEpochDay: Long)
}
