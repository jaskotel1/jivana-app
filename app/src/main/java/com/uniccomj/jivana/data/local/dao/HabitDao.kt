package com.uniccomj.jivana.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uniccomj.jivana.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits WHERE archivedAtEpochMillis IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT id FROM habits WHERE archivedAtEpochMillis IS NULL")
    suspend fun getActiveIds(): List<String>

    @Query("SELECT * FROM habits WHERE archivedAtEpochMillis IS NULL ORDER BY startEpochDay, id")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query(
        """
        UPDATE habits SET archivedAtEpochMillis = :archivedAt
        WHERE id = :id AND archivedAtEpochMillis IS NULL
        """
    )
    suspend fun archive(id: String, archivedAt: Long)

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
